package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
// READ_COMMITTED: the status is read after the group row lock, so it must see rows committed meanwhile
@Transactional(isolation = Isolation.READ_COMMITTED)
public class GroupDecisionWriterImpl implements GroupDecisionWriter {

    private static final String ACCESS_DENIED = "group not found or not owned by this company";

    private final PassengerRequestGroupRepository groupRepository;
    private final EmergencyRepository emergencyRepository;
    private final TripContextFacade tripFacade;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public GroupDecisionWriterImpl(PassengerRequestGroupRepository groupRepository,
                                   EmergencyRepository emergencyRepository,
                                   TripContextFacade tripFacade,
                                   CurrentUserProvider currentUserProvider,
                                   Clock clock) {
        this.groupRepository = groupRepository;
        this.emergencyRepository = emergencyRepository;
        this.tripFacade = tripFacade;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public ApproveGroupResult approve(ApproveGroupCommand command) {
        AuthenticatedUser caller = currentUserProvider.current();
        PassengerRequestGroup group = lockOwnGroup(command.groupId(), caller);

        if (group.getStatus() == GroupStatus.APPROVED) {
            Emergency existing = emergencyRepository.findByGroupId(group.getId())
                .orElseThrow(() -> new IllegalStateException("approved group without emergency"));
            return result(group, existing, true);
        }

        Long routeId = tripFacade.findShiftById(group.getShiftId())
            .orElseThrow(() -> new IllegalStateException("group shift not found"))
            .routeId();
        group.approve(caller.userId(), Instant.now(clock));
        Emergency emergency = Emergency.activateByPassengerGroup(UUID.randomUUID().toString(),
            group.getCompanyId(), group.getBusId(), group.getShiftId(), routeId, group.getId(), clock);
        groupRepository.saveAndFlush(group);
        emergencyRepository.saveAndFlush(emergency);
        return result(group, emergency, false);
    }

    @Override
    public DeclineGroupResult decline(DeclineGroupCommand command) {
        AuthenticatedUser caller = currentUserProvider.current();
        PassengerRequestGroup group = lockOwnGroup(command.groupId(), caller);

        if (group.getStatus() != GroupStatus.NOT_APPROVED) {
            group.decline(caller.userId(), command.reason(), command.response(), Instant.now(clock));
            groupRepository.saveAndFlush(group);
        }
        return new DeclineGroupResult(group.getId(), group.getStatus().name(), group.getDecidedAt(),
            group.getDecisionReason(), group.getUserResponse());
    }

    // The company is compared inside the lock against the token; a foreign or missing group is indistinguishable
    private PassengerRequestGroup lockOwnGroup(Long groupId, AuthenticatedUser caller) {
        if (groupId == null) {
            throw new ForbiddenOperationException("GROUP_ACCESS_DENIED", ACCESS_DENIED);
        }
        return groupRepository.findByIdForUpdate(groupId)
            .filter(g -> g.getCompanyId().equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("GROUP_ACCESS_DENIED", ACCESS_DENIED));
    }

    private ApproveGroupResult result(PassengerRequestGroup group, Emergency emergency, boolean replay) {
        return new ApproveGroupResult(group.getId(), emergency.getId(), group.getStatus().name(),
            emergency.getPriority().name(), group.getDecidedAt(), replay);
    }
}
