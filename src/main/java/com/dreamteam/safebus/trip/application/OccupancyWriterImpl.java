package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.shared.application.CountingSourcePrincipal;
import com.dreamteam.safebus.shared.application.CurrentCountingSourceProvider;
import com.dreamteam.safebus.shared.domain.CountingSourceAccess;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.CountingSource;
import com.dreamteam.safebus.trip.domain.model.EventOutcome;
import com.dreamteam.safebus.trip.domain.model.InitializationOutcome;
import com.dreamteam.safebus.trip.domain.model.OccupancyEvent;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.CountingSourceRepository;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.OccupancyEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when acquiring row locks inside this writer
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OccupancyWriterImpl implements OccupancyWriter {

    private final BusOccupancyRepository occupancyRepository;
    private final CountingSourceRepository sourceRepository;
    private final DriverShiftRepository shiftRepository;
    private final OccupancyEventRepository eventRepository;
    private final CurrentCountingSourceProvider currentSource;
    private final Clock clock;

    public OccupancyWriterImpl(BusOccupancyRepository occupancyRepository,
                               CountingSourceRepository sourceRepository,
                               DriverShiftRepository shiftRepository,
                               OccupancyEventRepository eventRepository,
                               CurrentCountingSourceProvider currentSource,
                               Clock clock) {
        this.occupancyRepository = occupancyRepository;
        this.sourceRepository = sourceRepository;
        this.shiftRepository = shiftRepository;
        this.eventRepository = eventRepository;
        this.currentSource = currentSource;
        this.clock = clock;
    }

    @Override
    public InitializeOccupancyResult initialize(InitializeOccupancyCommand command) {
        Instant now = Instant.now(clock);
        BusOccupancy.requireCaptureTimeNotInTheFuture(command.confirmedAt(), now);
        requireOwnActiveSource(command.busId());
        requireActiveShift(command.busId(), command.shiftId());
        BusOccupancy occupancy = lockOccupancy(command.busId());

        InitializationOutcome outcome = occupancy.initialize(command.initializationId(), command.shiftId(),
            command.confirmedCount(), command.confirmedAt(), now);
        if (outcome == InitializationOutcome.APPLIED) {
            occupancyRepository.saveAndFlush(occupancy);
        }
        return new InitializeOccupancyResult(command.initializationId(), command.busId(), command.shiftId(),
            command.confirmedCount(), command.confirmedAt().truncatedTo(ChronoUnit.MILLIS),
            outcome == InitializationOutcome.DUPLICATE);
    }

    @Override
    public RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command) {
        Instant now = Instant.now(clock);
        requireOwnActiveSource(command.busId());
        BusOccupancy occupancy = lockOccupancy(command.busId());
        occupancy.recordHeartbeat(now);
        occupancyRepository.saveAndFlush(occupancy);
        return new RecordHeartbeatResult(command.busId(), occupancy.getLastHeartbeatAt());
    }

    @Override
    public OccupancyEventWriteResult applyEvent(RecordOccupancyEventCommand command) {
        Instant now = Instant.now(clock);
        BusOccupancy.requireCaptureTimeNotInTheFuture(command.capturedAt(), now);
        requireOwnActiveSource(command.busId());
        requireActiveShift(command.busId(), command.shiftId());
        BusOccupancy occupancy = lockOccupancy(command.busId());

        var existing = eventRepository.findById(command.eventId());
        if (existing.isPresent()) {
            OccupancyEvent stored = existing.get();
            if (stored.hasSamePayload(command.busId(), command.shiftId(), command.type(),
                    command.sequence(), command.capturedAt())) {
                return new OccupancyEventWriteResult(EventOutcome.APPLIED, true, stored.getId(),
                    stored.getSequence(), stored.getType(), stored.getResultingCount(), 0);
            }
            throw new ConflictException("EVENT_ID_REUSED",
                "an event with this id already exists with a different payload");
        }
        if (eventRepository.findByBusIdAndEpochAndSequence(command.busId(), occupancy.getEpoch(),
                command.sequence()).isPresent()) {
            throw new ConflictException("SEQUENCE_ALREADY_APPLIED", "this sequence was already applied");
        }

        EventOutcome outcome = occupancy.apply(command.shiftId(), command.type(), command.sequence(),
            command.capturedAt());
        occupancyRepository.saveAndFlush(occupancy);
        if (outcome != EventOutcome.APPLIED) {
            return new OccupancyEventWriteResult(outcome, false, command.eventId(), command.sequence(),
                command.type(), occupancy.getCount(), occupancy.getLastSequence() + 1);
        }
        eventRepository.saveAndFlush(OccupancyEvent.applied(command.eventId(), occupancy, command.type(),
            command.sequence(), command.capturedAt(), clock));
        return new OccupancyEventWriteResult(EventOutcome.APPLIED, false, command.eventId(),
            command.sequence(), command.type(), occupancy.getCount(), 0);
    }

    // Every refusal is the same denial, so the caller cannot tell a wrong key from another bus
    private CountingSourcePrincipal requireOwnActiveSource(Long busId) {
        CountingSourcePrincipal principal = currentSource.current();
        if (!principal.busId().equals(busId)) {
            throw CountingSourceAccess.denied();
        }
        sourceRepository.findById(principal.sourceId())
            .filter(CountingSource::isActive)
            .orElseThrow(CountingSourceAccess::denied);
        return principal;
    }

    private void requireActiveShift(Long busId, Long shiftId) {
        boolean active = shiftRepository.findTopByBusIdAndStatusOrderByStartedAtDesc(busId, ShiftStatus.ACTIVE)
            .map(s -> s.getId().equals(shiftId))
            .orElse(false);
        if (!active) {
            throw CountingSourceAccess.denied();
        }
    }

    private BusOccupancy lockOccupancy(Long busId) {
        return occupancyRepository.findByBusIdForUpdate(busId).orElseThrow(CountingSourceAccess::denied);
    }
}
