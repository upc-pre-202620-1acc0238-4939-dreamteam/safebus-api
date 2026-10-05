package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
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
public class StartAttentionCommandServiceImpl implements StartAttentionCommandService {

    private static final String ACCESS_DENIED = "emergency not found or not owned by this company";

    private final EmergencyRepository emergencyRepository;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public StartAttentionCommandServiceImpl(EmergencyRepository emergencyRepository,
                                             CurrentUserProvider currentUserProvider,
                                             Clock clock) {
        this.emergencyRepository = emergencyRepository;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @Override
    public StartAttentionResult startAttention(String emergencyId) {
        AuthenticatedUser caller = currentUserProvider.current();

        // Take row lock BEFORE reading status to check ownership
        Emergency emergency = emergencyRepository.findByIdForUpdate(emergencyId)
            .filter(e -> e.getCompanyId().equals(caller.companyId()))
            .orElseThrow(() -> new ForbiddenOperationException("EMERGENCY_ACCESS_DENIED", ACCESS_DENIED));

        emergency.startAttention(caller.userId(), Instant.now(clock));
        emergencyRepository.saveAndFlush(emergency);

        return new StartAttentionResult(
            emergency.getId(), emergency.getStatus().name(),
            emergency.getResponsibleSupervisorUserId(),
            emergency.getAttentionStartedAt());
    }
}
