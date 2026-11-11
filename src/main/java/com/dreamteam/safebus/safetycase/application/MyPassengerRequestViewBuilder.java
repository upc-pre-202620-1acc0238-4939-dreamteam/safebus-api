package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

@Component
class MyPassengerRequestViewBuilder {

    static final String EVIDENCE_PATH = "/api/v1/passenger-requests/mine/%s/photo";

    private final PassengerRequestRepository requestRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final EmergencyRepository emergencyRepository;
    private final Clock clock;

    MyPassengerRequestViewBuilder(PassengerRequestRepository requestRepository,
                                  PassengerRequestGroupRepository groupRepository,
                                  EmergencyRepository emergencyRepository, Clock clock) {
        this.requestRepository = requestRepository;
        this.groupRepository = groupRepository;
        this.emergencyRepository = emergencyRepository;
        this.clock = clock;
    }

    MyPassengerRequestResult build(PassengerRequest request) {
        String evidence = EVIDENCE_PATH.formatted(request.getId());
        if (request.getGroupId() == null) {
            return new MyPassengerRequestResult(request.getId(), request.getMessage(), evidence,
                request.getReceivedAt(), null, null, PassengerRequestProgress.LATE, null);
        }
        PassengerRequestGroup group = groupRepository.findById(request.getGroupId()).orElseThrow();
        Optional<Emergency> emergency = emergencyRepository.findByGroupId(group.getId());
        int contributors = (int) requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED);
        return new MyPassengerRequestResult(request.getId(), request.getMessage(), evidence,
            request.getReceivedAt(), PublicReference.ofGroup(group.getId(), group.getPublicReference()),
            contributors, GroupProgress.of(group, emergency, Instant.now(clock)), decisionOf(group, emergency));
    }

    // The group's decision_reason is internal and never leaves this method.
    private MyPassengerRequestResult.Decision decisionOf(PassengerRequestGroup group, Optional<Emergency> emergency) {
        if (group.getStatus() == GroupStatus.NOT_APPROVED) {
            return new MyPassengerRequestResult.Decision("NOT_APPROVED", group.getDecidedAt(),
                group.getUserResponse());
        }
        if (group.getStatus() == GroupStatus.APPROVED || group.getStatus() == GroupStatus.CLOSED) {
            String response = emergency.filter(e -> e.getStatus() == EmergencyStatus.CLOSED)
                .map(Emergency::getUserResponse).orElse(null);
            return new MyPassengerRequestResult.Decision("APPROVED", group.getDecidedAt(), response);
        }
        return null;
    }
}
