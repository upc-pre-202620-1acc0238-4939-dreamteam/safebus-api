package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.passenger.interfaces.acl.PassengerContextFacade;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestPolicy;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.ImageInfo;
import com.dreamteam.safebus.shared.domain.ImagePolicy;
import com.dreamteam.safebus.shared.domain.InvalidImageException;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

@Service
// READ_COMMITTED: under MySQL's REPEATABLE READ a snapshot taken before the group lock would hide rows committed meanwhile
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PassengerRequestWriterImpl implements PassengerRequestWriter {

    private static final String ACTIVE = "ACTIVE";
    private static final String ENDED = "ENDED";

    private final PassengerRequestRepository requestRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final PassengerContextFacade passengerFacade;
    private final FleetContextFacade fleetFacade;
    private final ImageStoragePort imageStoragePort;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public PassengerRequestWriterImpl(PassengerRequestRepository requestRepository,
                                      PassengerRequestGroupRepository groupRepository,
                                      PassengerContextFacade passengerFacade,
                                      FleetContextFacade fleetFacade,
                                      ImageStoragePort imageStoragePort,
                                      CurrentUserProvider currentUserProvider,
                                      Clock clock) {
        this.requestRepository = requestRepository;
        this.groupRepository = groupRepository;
        this.passengerFacade = passengerFacade;
        this.fleetFacade = fleetFacade;
        this.imageStoragePort = imageStoragePort;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public RegisterPassengerRequestResult write(RegisterPassengerRequestCommand command) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);

        String id = PassengerRequest.normalizeId(command.id());
        String message = PassengerRequest.normalizeMessage(command.message());
        ImageInfo image = inspectPhoto(command.photo());
        Instant activatedAt = validateActivationTime(command.activatedAt(), now);

        Long callerUserId = currentUserProvider.current().userId();
        PassengerContextFacade.JourneyView journey = findOwnJourney(callerUserId, command.journeyId());

        String sha256 = sha256Of(command.photo());
        Optional<RegisterPassengerRequestResult> duplicate =
            findDuplicate(id, callerUserId, journey, message, activatedAt, sha256);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }
        // After the duplicate lookup: a retry of a request accepted before the end keeps its original answer.
        if (ENDED.equals(journey.status()) && activatedAt.isAfter(journey.endedAt())) {
            throw new RuleViolationException("ACTIVATION_AFTER_JOURNEY_END",
                "activatedAt must not be after the end of the journey");
        }

        Long companyId = fleetFacade.findBusCompanyId(journey.busId())
            .orElseThrow(() -> new RuleViolationException("BUS_NOT_FOUND", "bus not found"));

        boolean eligible = ACTIVE.equals(journey.status())
            && Duration.between(activatedAt, now).compareTo(PassengerRequestPolicy.MAX_REQUEST_AGE) <= 0;
        if (!eligible) {
            Long imageId = imageStoragePort.store(command.photo(), image.contentType());
            PassengerRequest late = PassengerRequest.create(id, null, callerUserId, journey.journeyId(),
                journey.busId(), journey.shiftId(), companyId, message, imageId, sha256, activatedAt, now,
                RequestStatus.LATE);
            requestRepository.saveAndFlush(late);
            return new RegisterPassengerRequestResult(late.getId(), late.getStatus().name(),
                late.getReceivedAt(), null, null, false);
        }

        Optional<PassengerRequestGroup> openGroup =
            groupRepository.findOpenByShiftIdForUpdate(journey.shiftId());
        if (openGroup.isPresent() && openGroup.get().isWindowOverdue(now)) {
            // Flush the expiry before any new group is inserted: Hibernate runs INSERTs before UPDATEs,
            // and the unique (shift_id, open_marker) constraint would otherwise be violated.
            openGroup.get().expire();
            groupRepository.saveAndFlush(openGroup.get());
            openGroup = Optional.empty();
        }

        // Re-check under the group lock: an identical retry that waited here must see the committed original.
        duplicate = findDuplicate(id, callerUserId, journey, message, activatedAt, sha256);
        if (duplicate.isPresent()) {
            return duplicate.get();
        }

        if (openGroup.isPresent()
                && requestRepository.existsByGroupIdAndPassengerUserId(openGroup.get().getId(), callerUserId)) {
            throw new ConflictException("DUPLICATE_CONTRIBUTION",
                "this account already contributed to the current group");
        }

        PassengerRequestGroup group = openGroup.orElseGet(() -> groupRepository.saveAndFlush(
            PassengerRequestGroup.open(companyId, journey.busId(), journey.shiftId(), now)));

        Long imageId = imageStoragePort.store(command.photo(), image.contentType());
        boolean collecting = group.getStatus() == GroupStatus.COLLECTING;
        PassengerRequest request = PassengerRequest.create(id, group.getId(), callerUserId, journey.journeyId(),
            journey.busId(), journey.shiftId(), companyId, message, imageId, sha256, activatedAt, now,
            collecting ? RequestStatus.COUNTED : RequestStatus.ATTACHED);
        requestRepository.saveAndFlush(request);

        long contributors = requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED);
        if (collecting && contributors >= PassengerRequestPolicy.REQUIRED_CONTRIBUTORS) {
            group.moveToAwaitingApproval(now);
            groupRepository.saveAndFlush(group);
        }
        return new RegisterPassengerRequestResult(request.getId(), request.getStatus().name(),
            request.getReceivedAt(), group.getStatus().name(), (int) contributors, false);
    }

    private ImageInfo inspectPhoto(byte[] photo) {
        if (photo == null || photo.length == 0) {
            throw new RuleViolationException("EVIDENCE_PHOTO_REQUIRED", "an incident photo is required");
        }
        try {
            return ImagePolicy.inspect(photo);
        } catch (InvalidImageException ex) {
            if (ex.getReason() == InvalidImageException.Reason.TOO_LARGE) {
                throw new RuleViolationException("EVIDENCE_PHOTO_TOO_LARGE", "incident photo exceeds 5 MiB");
            }
            throw new RuleViolationException("EVIDENCE_PHOTO_INVALID",
                "incident photo is not a valid JPEG or PNG");
        }
    }

    private Instant validateActivationTime(Instant activatedAt, Instant now) {
        if (activatedAt == null) {
            throw new RuleViolationException("INVALID_ACTIVATION_TIME", "activatedAt is required");
        }
        Instant truncated = activatedAt.truncatedTo(ChronoUnit.MILLIS);
        if (truncated.isAfter(now.plus(PassengerRequestPolicy.MAX_FUTURE_SKEW))) {
            throw new RuleViolationException("INVALID_ACTIVATION_TIME",
                "activatedAt must not be more than 5 minutes in the future");
        }
        return truncated;
    }

    private PassengerContextFacade.JourneyView findOwnJourney(Long callerUserId, Long journeyId) {
        if (journeyId == null) {
            throw new RuleViolationException("INVALID_REQUEST", "journeyId is required");
        }
        return passengerFacade.findJourneyOfPassenger(callerUserId, journeyId)
            .orElseThrow(() -> new ForbiddenOperationException("JOURNEY_ACCESS_DENIED",
                "the journey does not belong to this account"));
    }

    private Optional<RegisterPassengerRequestResult> findDuplicate(String id, Long callerUserId,
            PassengerContextFacade.JourneyView journey, String message, Instant activatedAt, String sha256) {
        Optional<PassengerRequest> existing = requestRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        PassengerRequest stored = existing.get();
        if (!stored.hasSamePayloadAs(callerUserId, journey.journeyId(), message, activatedAt, sha256)) {
            throw new ConflictException("REQUEST_ID_REUSED", "this request id is already in use");
        }
        Optional<PassengerRequestGroup> group = stored.getGroupId() == null
            ? Optional.empty() : groupRepository.findById(stored.getGroupId());
        Integer contributors = group.map(g ->
            (int) requestRepository.countByGroupIdAndStatus(g.getId(), RequestStatus.COUNTED)).orElse(null);
        return Optional.of(new RegisterPassengerRequestResult(stored.getId(), stored.getStatus().name(),
            stored.getReceivedAt(), group.map(g -> g.getStatus().name()).orElse(null), contributors, true));
    }

    private static String sha256Of(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
