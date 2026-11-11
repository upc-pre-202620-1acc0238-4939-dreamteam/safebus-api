package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
// READ_COMMITTED prevents MVCC snapshot gap when reading status after acquiring the row lock
@Transactional(isolation = Isolation.READ_COMMITTED)
public class CloseEmergencyCommandServiceImpl implements CloseEmergencyCommandService {

    private static final String ACCESS_DENIED = "emergency not found or not owned by this company";

    private final EmergencyRepository emergencyRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public CloseEmergencyCommandServiceImpl(EmergencyRepository emergencyRepository,
                                             PassengerRequestGroupRepository groupRepository,
                                             CurrentUserProvider currentUserProvider,
                                             Clock clock) {
        this.emergencyRepository = emergencyRepository;
        this.groupRepository = groupRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public CloseEmergencyResult close(CloseEmergencyCommand command) {
        AuthenticatedUser caller = currentUserProvider.current();

        // Lock order is group first, then emergency, as in approval: peek unlocked to learn the group id
        Emergency peeked = emergencyRepository.findById(command.id())
            .filter(e -> e.getCompanyId().equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("EMERGENCY_ACCESS_DENIED", ACCESS_DENIED));
        PassengerRequestGroup group = peeked.getGroupId() == null ? null
            : groupRepository.findByIdForUpdate(peeked.getGroupId())
                .orElseThrow(() -> new IllegalStateException("emergency group not found"));

        // Take row lock BEFORE reading status to check ownership
        Emergency emergency = emergencyRepository.findByIdForUpdate(command.id())
            .filter(e -> e.getCompanyId().equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("EMERGENCY_ACCESS_DENIED", ACCESS_DENIED));

        emergency.close(command.outcome(), command.userResponse(), Instant.now(clock));
        emergencyRepository.saveAndFlush(emergency);
        if (group != null && group.getStatus() == GroupStatus.APPROVED) {
            group.close();
            groupRepository.saveAndFlush(group);
        }

        return new CloseEmergencyResult(
            emergency.getId(), emergency.getStatus().name(),
            emergency.getClosedAt(), emergency.getOutcome());
    }
}
