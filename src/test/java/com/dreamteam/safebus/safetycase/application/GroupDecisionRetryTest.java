package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GroupDecisionRetryTest {

    private static final ApproveGroupResult APPROVED = new ApproveGroupResult(
        1L, "e1", "APPROVED", "HIGH", Instant.parse("2030-01-01T10:00:00Z"), false);
    private static final DeclineGroupResult DECLINED = new DeclineGroupResult(
        1L, "NOT_APPROVED", Instant.parse("2030-01-01T10:00:00Z"), "reason", null);
    private static final ApproveGroupCommand APPROVE = new ApproveGroupCommand(1L);
    private static final DeclineGroupCommand DECLINE = new DeclineGroupCommand(1L, "reason", null);

    @Autowired ApproveGroupCommandService approveService;
    @Autowired DeclineGroupCommandService declineService;
    @MockitoBean GroupDecisionWriter writer;

    @Test
    void approveRetriesOnceAfterAConstraintViolation() {
        when(writer.approve(any())).thenThrow(new DataIntegrityViolationException("simulated")).thenReturn(APPROVED);

        assertEquals(APPROVED, approveService.approve(APPROVE));
        verify(writer, times(2)).approve(any());
    }

    @Test
    void approveRetriesOnceAfterAnOptimisticLockFailure() {
        when(writer.approve(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException("PassengerRequestGroup", 1L))
            .thenReturn(APPROVED);

        assertEquals(APPROVED, approveService.approve(APPROVE));
        verify(writer, times(2)).approve(any());
    }

    @Test
    void approveDoesNotRetryTwice() {
        when(writer.approve(any())).thenThrow(new DataIntegrityViolationException("simulated"));

        assertThrows(DataIntegrityViolationException.class, () -> approveService.approve(APPROVE));
        verify(writer, times(2)).approve(any());
    }

    @Test
    void approveNeverRetriesADomainError() {
        when(writer.approve(any())).thenThrow(new ConflictException("INVALID_TRANSITION", "x"));

        assertThrows(ConflictException.class, () -> approveService.approve(APPROVE));
        verify(writer, times(1)).approve(any());
    }

    @Test
    void declineRetriesOnceAfterAConstraintViolation() {
        when(writer.decline(any())).thenThrow(new DataIntegrityViolationException("simulated")).thenReturn(DECLINED);

        assertEquals(DECLINED, declineService.decline(DECLINE));
        verify(writer, times(2)).decline(any());
    }

    @Test
    void declineDoesNotRetryTwice() {
        when(writer.decline(any())).thenThrow(new DataIntegrityViolationException("simulated"));

        assertThrows(DataIntegrityViolationException.class, () -> declineService.decline(DECLINE));
        verify(writer, times(2)).decline(any());
    }

    @Test
    void declineNeverRetriesADomainError() {
        when(writer.decline(any())).thenThrow(new ConflictException("INVALID_TRANSITION", "x"));

        assertThrows(ConflictException.class, () -> declineService.decline(DECLINE));
        verify(writer, times(1)).decline(any());
    }
}
