package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
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
class CloseEmergencyCommandServiceTest {

    private static final Long COMPANY_ID    = 100L;
    private static final Long SUPERVISOR_ID = 999L;
    private static final Long OTHER_COMPANY = 200L;

    @Autowired CloseEmergencyCommandService  closeService;
    @Autowired StartAttentionCommandService  attentionService;
    @Autowired EmergencyRepository           emergencyRepository;
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

    private CloseEmergencyCommand closeCmd(String outcome) {
        return new CloseEmergencyCommand(active.getId(), outcome, "response");
    }

    @Test
    void close_validFromInProgress_returnsClosed() {
        attentionService.startAttention(active.getId());
        CloseEmergencyResult result = closeService.close(closeCmd("all resolved"));
        assertEquals(EmergencyStatus.CLOSED.name(), result.status());
        assertEquals("all resolved", result.outcome());
        assertNotNull(result.closedAt());
    }

    @Test
    void close_fromActive_throwsAttentionNotStarted() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(closeCmd("outcome")));
        assertEquals("ATTENTION_NOT_STARTED", ex.code());
        assertEquals(EmergencyStatus.ACTIVE,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_blankOutcome_throwsOutcomeRequired() {
        attentionService.startAttention(active.getId());
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(new CloseEmergencyCommand(active.getId(), "", null)));
        assertEquals("OUTCOME_REQUIRED", ex.code());
        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_nullOutcome_throwsOutcomeRequired() {
        attentionService.startAttention(active.getId());
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(new CloseEmergencyCommand(active.getId(), null, null)));
        assertEquals("OUTCOME_REQUIRED", ex.code());
    }

    @Test
    void close_outcomeTooLong_throwsOutcomeTooLong() {
        attentionService.startAttention(active.getId());
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(new CloseEmergencyCommand(active.getId(), "x".repeat(501), null)));
        assertEquals("OUTCOME_TOO_LONG", ex.code());
        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_userResponseTooLong_throwsResponseTooLong() {
        attentionService.startAttention(active.getId());
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(new CloseEmergencyCommand(active.getId(), "ok", "y".repeat(501))));
        assertEquals("RESPONSE_TOO_LONG", ex.code());
        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_alreadyClosed_throwsInvalidTransition() {
        attentionService.startAttention(active.getId());
        closeService.close(closeCmd("outcome"));
        ConflictException ex = assertThrows(ConflictException.class,
            () -> closeService.close(closeCmd("outcome2")));
        assertEquals("INVALID_TRANSITION", ex.code());
        assertEquals(EmergencyStatus.CLOSED,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_anotherCompany_throwsAccessDenied() {
        attentionService.startAttention(active.getId());
        mockSupervisor(888L, OTHER_COMPANY);
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> closeService.close(closeCmd("outcome")));
        assertEquals("EMERGENCY_ACCESS_DENIED", ex.code());
        assertEquals(EmergencyStatus.IN_PROGRESS,
            emergencyRepository.findById(active.getId()).orElseThrow().getStatus());
    }

    @Test
    void close_nonExistentId_sameAsAnotherCompany() {
        attentionService.startAttention(active.getId());
        mockSupervisor(888L, OTHER_COMPANY);
        ForbiddenOperationException fromAnother = assertThrows(ForbiddenOperationException.class,
            () -> closeService.close(closeCmd("outcome")));

        mockSupervisor(SUPERVISOR_ID, COMPANY_ID);
        ForbiddenOperationException fromMissing = assertThrows(ForbiddenOperationException.class,
            () -> closeService.close(new CloseEmergencyCommand(
                "nonexistent-0000000000000000000000000", "outcome", null)));

        assertEquals(fromAnother.code(),    fromMissing.code());
        assertEquals(fromAnother.getMessage(), fromMissing.getMessage());
    }
}
