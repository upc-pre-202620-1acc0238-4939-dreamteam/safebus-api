package com.dreamteam.safebus.contact.application;

import com.dreamteam.safebus.contact.domain.model.ContactRequest;
import com.dreamteam.safebus.contact.domain.repository.ContactRequestRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class RegisterContactRequestTest {

    private static final String ID = "12345678-1234-1234-1234-123456789abc";
    private static final RegisterContactRequestCommand COMMAND =
        new RegisterContactRequestCommand(ID, "Safe Bus", "Ana", "ana@example.com", true);
    private static final RegisterContactRequestResult RESULT =
        new RegisterContactRequestResult("CR-012345ABCD", Instant.parse("2030-01-01T10:00:00.123Z"), false);

    @Autowired RegisterContactRequest service;
    @Autowired ContactRequestRepository repository;
    @MockitoBean ContactRequestWriter writer;

    static Stream<RuntimeException> retryableFailures() {
        return Stream.of(new DataIntegrityViolationException("simulated collision"),
            new ObjectOptimisticLockingFailureException(ContactRequest.class, ID));
    }

    static Stream<RuntimeException> domainFailures() {
        return Stream.of(new ConflictException("SUBMISSION_ID_REUSED", "submissionId was reused"),
            new RuleViolationException("CONSENT_REQUIRED", "consent must be true"));
    }

    @Test
    void register_successCallsWriterOnceWithoutAnOrchestratorTransaction() {
        when(writer.write(COMMAND)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RESULT;
        });

        assertEquals(RESULT, service.register(COMMAND));

        verify(writer, times(1)).write(COMMAND);
    }

    @ParameterizedTest
    @MethodSource("retryableFailures")
    void register_retriesEitherPersistenceFailureOnceOutsideATransaction(RuntimeException failure) {
        when(writer.write(COMMAND)).thenThrow(failure).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return RESULT;
        });

        assertEquals(RESULT, service.register(COMMAND));

        verify(writer, times(2)).write(COMMAND);
    }

    @ParameterizedTest
    @MethodSource("retryableFailures")
    void register_secondPersistenceFailureEscapesWithoutAThirdAttempt(RuntimeException failure) {
        when(writer.write(COMMAND)).thenThrow(failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> service.register(COMMAND)));

        verify(writer, times(2)).write(COMMAND);
        assertTrue(repository.findAll().isEmpty());
    }

    @ParameterizedTest
    @MethodSource("domainFailures")
    void register_domainRejectionEscapesWithoutRetry(RuntimeException failure) {
        when(writer.write(COMMAND)).thenThrow(failure);

        assertSame(failure, assertThrows(RuntimeException.class, () -> service.register(COMMAND)));

        verify(writer, times(1)).write(COMMAND);
        assertTrue(repository.findAll().isEmpty());
    }
}
