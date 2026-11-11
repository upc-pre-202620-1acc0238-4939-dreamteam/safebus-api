package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RegisterPassengerRequestRetryTest {

    private static final RegisterPassengerRequestResult RESULT = new RegisterPassengerRequestResult(
        "id", "COUNTED", Instant.parse("2030-01-01T10:00:00Z"), "COLLECTING", 1, false, 5L, false);
    private static final RegisterPassengerRequestCommand COMMAND =
        new RegisterPassengerRequestCommand("id", 1L, "m", Instant.now(), new byte[] {1});

    @Autowired RegisterPassengerRequest orchestrator;
    @MockitoBean PassengerRequestWriter writer;
    @MockitoBean SupervisorNotifier notifier;

    @Test
    void retriesOnceAfterAConstraintViolation() {
        when(writer.write(any())).thenThrow(new DataIntegrityViolationException("simulated")).thenReturn(RESULT);

        assertEquals(RESULT, orchestrator.register(COMMAND));
        verify(writer, times(2)).write(any());
    }

    @Test
    void retriesOnceAfterAnOptimisticLockFailure() {
        when(writer.write(any()))
            .thenThrow(new ObjectOptimisticLockingFailureException("PassengerRequestGroup", 1L))
            .thenReturn(RESULT);

        assertEquals(RESULT, orchestrator.register(COMMAND));
        verify(writer, times(2)).write(any());
    }

    @Test
    void aSecondFailureEscapesInsteadOfRetryingAgain() {
        when(writer.write(any())).thenThrow(new DataIntegrityViolationException("simulated"));

        assertThrows(DataIntegrityViolationException.class, () -> orchestrator.register(COMMAND));
        verify(writer, times(2)).write(any());
    }

    @Test
    void aRuleViolationIsNeverRetried() {
        when(writer.write(any())).thenThrow(new RuleViolationException("MESSAGE_REQUIRED", "x"));

        assertThrows(RuleViolationException.class, () -> orchestrator.register(COMMAND));
        verify(writer, times(1)).write(any());
    }

    @Test
    void reviewRequiredIsAnnouncedOnceEvenWhenTheFirstAttemptNeededARetry() {
        RegisterPassengerRequestResult crossed = new RegisterPassengerRequestResult("id", "COUNTED",
            Instant.parse("2030-01-01T10:00:00Z"), "AWAITING_APPROVAL", 3, false, 5L, true);
        when(writer.write(any())).thenThrow(new DataIntegrityViolationException("simulated")).thenReturn(crossed);

        orchestrator.register(COMMAND);

        verify(notifier, times(1)).groupReviewRequired(5L);
    }

    @Test
    void nothingIsAnnouncedWhenTheThresholdWasNotCrossedOrTheRequestIsADuplicate() {
        RegisterPassengerRequestResult duplicate = new RegisterPassengerRequestResult("id", "COUNTED",
            Instant.parse("2030-01-01T10:00:00Z"), "AWAITING_APPROVAL", 3, true, 5L, false);
        when(writer.write(any())).thenReturn(RESULT).thenReturn(duplicate);

        orchestrator.register(COMMAND);
        orchestrator.register(COMMAND);

        verify(notifier, never()).groupReviewRequired(any());
    }
}
