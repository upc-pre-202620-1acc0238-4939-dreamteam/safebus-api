package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class StartAttentionCommandServiceTest {

    private static final Long COMPANY_ID   = 100L;
    private static final Long SUPERVISOR_ID = 999L;
    private static final Long OTHER_COMPANY = 200L;

    @Autowired StartAttentionCommandService service;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired Clock clock;

    @MockitoBean CurrentUserProvider currentUserProvider;

    private Emergency active;

    @BeforeEach
    void setUp() {
        mockSupervisor(SUPERVISOR_ID, COMPANY_ID);
        active = save(COMPANY_ID);
    }

    @AfterEach
    void tearDown() {
        emergencyRepository.deleteAll();
    }

    private void mockSupervisor(long userId, long companyId) {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(userId, "SUPERVISOR", companyId));
    }

    private Emergency save(long companyId) {
        return emergencyRepository.save(
            Emergency.activateByDriver(
                UUID.randomUUID().toString(),
                companyId, 10L, 300L, 200L, 400L, null,
                Instant.now(clock).minusSeconds(60), clock));
    }

    @Test
    void startAttention_valid_returnsInProgress() {
        StartAttentionResult result = service.startAttention(active.getId());
        assertEquals(EmergencyStatus.IN_PROGRESS.name(), result.status());
        assertEquals(SUPERVISOR_ID, result.responsibleSupervisorId());
        assertNotNull(result.attentionStartedAt());
    }

    @Test
    void startAttention_again_throwsInvalidTransition() {
        service.startAttention(active.getId());
        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.startAttention(active.getId()));
        assertEquals("INVALID_TRANSITION", ex.code());
        Emergency reloaded = emergencyRepository.findById(active.getId()).orElseThrow();
        assertEquals(EmergencyStatus.IN_PROGRESS, reloaded.getStatus());
        assertEquals(SUPERVISOR_ID, reloaded.getResponsibleSupervisorUserId());
    }

    @Test
    void startAttention_anotherCompany_throwsAccessDenied() {
        mockSupervisor(888L, OTHER_COMPANY);
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.startAttention(active.getId()));
        assertEquals("EMERGENCY_ACCESS_DENIED", ex.code());
        assertEquals(EmergencyStatus.ACTIVE,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void startAttention_nonExistentId_sameAsAnotherCompany() {
        ForbiddenOperationException fromAnother;
        ForbiddenOperationException fromMissing;

        mockSupervisor(888L, OTHER_COMPANY);
        fromAnother = assertThrows(ForbiddenOperationException.class,
            () -> service.startAttention(active.getId()));

        mockSupervisor(SUPERVISOR_ID, COMPANY_ID);
        fromMissing = assertThrows(ForbiddenOperationException.class,
            () -> service.startAttention("nonexistent-0000000000000000000000000"));

        assertEquals(fromAnother.code(),    fromMissing.code());
        assertEquals(fromAnother.getMessage(), fromMissing.getMessage());
    }
}
