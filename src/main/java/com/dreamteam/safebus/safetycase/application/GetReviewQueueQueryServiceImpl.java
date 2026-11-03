package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetReviewQueueQueryServiceImpl implements GetReviewQueueQueryService {

    private static final Set<EmergencyStatus> OPEN = EnumSet.of(EmergencyStatus.ACTIVE, EmergencyStatus.IN_PROGRESS);

    // ACTIVE before IN_PROGRESS, then oldest received first, then id as the deterministic tiebreaker
    private static final Comparator<Emergency> EMERGENCY_ORDER = Comparator
        .comparing((Emergency e) -> e.getStatus() == EmergencyStatus.ACTIVE ? 0 : 1)
        .thenComparing(Emergency::getReceivedAt)
        .thenComparing(Emergency::getId);

    private static final Comparator<PassengerRequestGroup> GROUP_ORDER = Comparator
        .comparing(PassengerRequestGroup::getAwaitingSince)
        .thenComparing(PassengerRequestGroup::getId);

    private static final Comparator<PassengerRequest> REQUEST_ORDER = Comparator
        .comparing(PassengerRequest::getReceivedAt)
        .thenComparing(PassengerRequest::getId);

    private final EmergencyRepository emergencyRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final PassengerRequestRepository requestRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public GetReviewQueueQueryServiceImpl(EmergencyRepository emergencyRepository,
                                          PassengerRequestGroupRepository groupRepository,
                                          PassengerRequestRepository requestRepository,
                                          CurrentUserProvider currentUserProvider,
                                          Clock clock) {
        this.emergencyRepository = emergencyRepository;
        this.groupRepository = groupRepository;
        this.requestRepository = requestRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public GetReviewQueueResult get() {
        AuthenticatedUser caller = currentUserProvider.current();
        Long companyId = caller.companyId();
        if (companyId == null) {
            throw new ForbiddenOperationException("REVIEW_ACCESS_DENIED", "the token carries no company");
        }

        List<GetReviewQueueResult.EmergencyItem> driverEmergencies = emergenciesOf(companyId, EmergencySource.DRIVER);
        List<GetReviewQueueResult.EmergencyItem> passengerEmergencies =
            emergenciesOf(companyId, EmergencySource.PASSENGER_GROUP);

        List<PassengerRequestGroup> groups = groupRepository
            .findByCompanyIdAndStatus(companyId, GroupStatus.AWAITING_APPROVAL).stream()
            .sorted(GROUP_ORDER).toList();
        Map<Long, List<PassengerRequest>> requestsByGroup = groups.isEmpty() ? Map.of()
            : requestRepository.findByGroupIdIn(groups.stream().map(PassengerRequestGroup::getId).toList())
                .stream().collect(Collectors.groupingBy(PassengerRequest::getGroupId));

        return new GetReviewQueueResult(Instant.now(clock), driverEmergencies, passengerEmergencies,
            groups.stream().map(g -> toItem(g, requestsByGroup.getOrDefault(g.getId(), List.of()))).toList());
    }

    private List<GetReviewQueueResult.EmergencyItem> emergenciesOf(Long companyId, EmergencySource source) {
        return emergencyRepository.findByCompanyIdAndSourceAndStatusIn(companyId, source, OPEN).stream()
            .sorted(EMERGENCY_ORDER).map(GetReviewQueueQueryServiceImpl::toItem).toList();
    }

    private static GetReviewQueueResult.EmergencyItem toItem(Emergency e) {
        return new GetReviewQueueResult.EmergencyItem(e.getId(), e.getGroupId(), e.getStatus().name(),
            e.getPriority().name(), e.getBusId(), e.getShiftId(), e.getRouteId(), e.getDriverId(),
            e.getPoint() == null ? null : e.getPoint().getLatitude(),
            e.getPoint() == null ? null : e.getPoint().getLongitude(),
            e.getActivatedAt(), e.getReceivedAt(), e.getResponsibleSupervisorUserId(),
            e.getAttentionStartedAt());
    }

    private static GetReviewQueueResult.GroupItem toItem(PassengerRequestGroup g, List<PassengerRequest> requests) {
        List<GetReviewQueueResult.RequestItem> items = requests.stream().sorted(REQUEST_ORDER)
            .map(r -> new GetReviewQueueResult.RequestItem(r.getId(), r.getMessage(), r.getActivatedAt(),
                r.getReceivedAt(), r.getStatus().name()))
            .toList();
        int contributors = (int) requests.stream().filter(r -> r.getStatus() == RequestStatus.COUNTED).count();
        return new GetReviewQueueResult.GroupItem(g.getId(), g.getBusId(), g.getShiftId(),
            g.getWindowStartedAt(), g.getAwaitingSince(), contributors, requests.size(), items);
    }
}
