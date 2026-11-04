package com.dreamteam.safebus.safetycase.interfaces.acl;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SafetycaseContextFacade {

    public record OpenEmergencyView(String emergencyId, Long busId, Long shiftId, String source,
                                    String priority, String status, Instant activatedAt,
                                    Instant receivedAt, Instant attentionStartedAt) {}

    // No group id, passenger, message or evidence: only what a fleet overview may show; emergencyStatus is null until approval
    public record OpenGroupView(Long busId, String status, int requestCount, Instant windowStartedAt,
                                Instant windowExpiresAt, Instant awaitingSince, String emergencyStatus) {}

    private static final List<EmergencyStatus> OPEN_STATUSES =
        List.of(EmergencyStatus.ACTIVE, EmergencyStatus.IN_PROGRESS);

    private static final List<GroupStatus> OPEN_GROUP_STATUSES =
        List.of(GroupStatus.COLLECTING, GroupStatus.AWAITING_APPROVAL, GroupStatus.APPROVED);

    private final EmergencyRepository emergencyRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final PassengerRequestRepository requestRepository;
    private final Clock clock;

    public SafetycaseContextFacade(EmergencyRepository emergencyRepository,
                                   PassengerRequestGroupRepository groupRepository,
                                   PassengerRequestRepository requestRepository,
                                   Clock clock) {
        this.emergencyRepository = emergencyRepository;
        this.groupRepository = groupRepository;
        this.requestRepository = requestRepository;
        this.clock = clock;
    }

    // Open means ACTIVE or IN_PROGRESS, whatever the state of the shift; newest received first
    @Transactional(readOnly = true)
    public List<OpenEmergencyView> findOpenEmergenciesOfCompany(Long companyId) {
        return emergencyRepository
            .findByCompanyIdAndStatusInOrderByReceivedAtDesc(companyId, OPEN_STATUSES).stream()
            .map(e -> new OpenEmergencyView(e.getId(), e.getBusId(), e.getShiftId(),
                e.getSource().name(), e.getPriority().name(), e.getStatus().name(),
                e.getActivatedAt(), e.getReceivedAt(), e.getAttentionStartedAt()))
            .toList();
    }

    // Open means COLLECTING, AWAITING_APPROVAL or APPROVED; a COLLECTING group past its window is not shown; oldest window first
    @Transactional(readOnly = true)
    public List<OpenGroupView> findOpenGroupsOfCompany(Long companyId) {
        Instant now = Instant.now(clock);
        List<PassengerRequestGroup> groups = groupRepository
            .findByCompanyIdAndStatusIn(companyId, OPEN_GROUP_STATUSES).stream()
            .filter(g -> !g.isWindowOverdue(now))
            .sorted(Comparator.comparing(PassengerRequestGroup::getWindowStartedAt)
                .thenComparing(PassengerRequestGroup::getId))
            .toList();
        if (groups.isEmpty()) {
            return List.of();
        }
        List<Long> ids = groups.stream().map(PassengerRequestGroup::getId).toList();
        Map<Long, Long> requestCounts = requestRepository.findByGroupIdIn(ids).stream()
            .collect(Collectors.groupingBy(PassengerRequest::getGroupId, Collectors.counting()));
        Map<Long, Emergency> emergencies = emergencyRepository.findByGroupIdIn(ids).stream()
            .collect(Collectors.toMap(Emergency::getGroupId, e -> e));
        return groups.stream()
            .map(g -> new OpenGroupView(g.getBusId(), g.getStatus().name(),
                requestCounts.getOrDefault(g.getId(), 0L).intValue(),
                g.getWindowStartedAt(), g.getWindowExpiresAt(), g.getAwaitingSince(),
                emergencies.containsKey(g.getId()) ? emergencies.get(g.getId()).getStatus().name() : null))
            .toList();
    }
}
