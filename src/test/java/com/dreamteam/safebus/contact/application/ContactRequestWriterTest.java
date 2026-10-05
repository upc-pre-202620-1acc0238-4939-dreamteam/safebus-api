package com.dreamteam.safebus.contact.application;

import com.dreamteam.safebus.contact.domain.model.ContactRequest;
import com.dreamteam.safebus.contact.domain.port.ReceiptReferenceGenerator;
import com.dreamteam.safebus.contact.domain.repository.ContactRequestRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ContactRequestWriterTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00.123456789Z");
    private static final Instant RECEIVED_AT = Instant.parse("2030-01-01T10:00:00.123Z");

    @Autowired ContactRequestWriter writer;
    @Autowired ContactRequestRepository repository;
    @MockitoBean ReceiptReferenceGenerator referenceGenerator;
    @MockitoBean Clock clock;

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        when(referenceGenerator.generate()).thenReturn("CR-012345ABCD");
    }

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    private RegisterContactRequestCommand command(String id) {
        return new RegisterContactRequestCommand(id, "  Safe Bus  ", "  Ana  ", "  Ana@Example.com  ", true);
    }

    @Test
    void write_newRequestStoresEveryFieldAndUsesReadCommittedTransaction() {
        String id = UUID.randomUUID().toString();
        when(referenceGenerator.generate()).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            return "CR-012345ABCD";
        });

        RegisterContactRequestResult result = writer.write(command(id));

        assertEquals(new RegisterContactRequestResult("CR-012345ABCD", RECEIVED_AT, false), result);
        assertEquals(1L, repository.count());
        ContactRequest stored = repository.findById(id).orElseThrow();
        assertEquals(id, stored.getId());
        assertEquals("CR-012345ABCD", stored.getReceiptReference());
        assertEquals("Safe Bus", stored.getCompanyName());
        assertEquals("Ana", stored.getContactName());
        assertEquals("Ana@Example.com", stored.getEmail());
        assertTrue(stored.isConsentGiven());
        assertEquals(RECEIVED_AT, stored.getReceivedAt());
    }

    @Test
    void write_normalizedRetryReturnsOriginalReceiptWithoutWriting() {
        String id = UUID.randomUUID().toString();
        RegisterContactRequestResult first = writer.write(command(id));
        ContactRequest before = repository.findById(id).orElseThrow();
        when(clock.instant()).thenReturn(NOW.plusSeconds(60));

        RegisterContactRequestResult retry = writer.write(
            new RegisterContactRequestCommand(id, "Safe Bus", "Ana", "ana@example.com", true));

        assertTrue(retry.duplicate());
        assertEquals(first.receiptReference(), retry.receiptReference());
        assertEquals(first.receivedAt(), retry.receivedAt());
        verify(referenceGenerator, times(1)).generate();
        assertUnchanged(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {"companyName", "contactName", "email", "consent"})
    void write_reusedIdWithChangedFieldRejectsAndPreservesEveryField(String field) {
        String id = UUID.randomUUID().toString();
        writer.write(command(id));
        ContactRequest before = repository.findById(id).orElseThrow();
        RegisterContactRequestCommand changed = new RegisterContactRequestCommand(id,
            field.equals("companyName") ? "Other Company" : "Safe Bus",
            field.equals("contactName") ? "Other Contact" : "Ana",
            field.equals("email") ? "other@example.com" : "Ana@Example.com",
            !field.equals("consent"));

        ConflictException ex = assertThrows(ConflictException.class, () -> writer.write(changed));

        assertEquals("SUBMISSION_ID_REUSED", ex.code());
        assertEquals("submissionId has already been used with a different payload", ex.getMessage());
        assertUnchanged(before);
        verify(referenceGenerator, times(1)).generate();
    }

    static Stream<Arguments> invalidPayloads() {
        return Stream.of(
            Arguments.of(null, "Ana", "a@example.com", true, "INVALID_COMPANY_NAME"),
            Arguments.of("A".repeat(101), "Ana", "a@example.com", true, "INVALID_COMPANY_NAME"),
            Arguments.of("Safe Bus", null, "a@example.com", true, "INVALID_CONTACT_NAME"),
            Arguments.of("Safe Bus", "A".repeat(101), "a@example.com", true, "INVALID_CONTACT_NAME"),
            Arguments.of("Safe Bus", "Ana", null, true, "INVALID_EMAIL"),
            Arguments.of("Safe Bus", "Ana", "invalid", true, "INVALID_EMAIL"),
            Arguments.of("Safe Bus", "Ana", "a@example.com", false, "CONSENT_REQUIRED")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidPayloads")
    void write_invalidPayloadStoresNothingAndPreservesExistingRequest(
            String company, String contact, String email, boolean consent, String code) {
        ContactRequest before = seedRequest();
        String id = UUID.randomUUID().toString();
        RegisterContactRequestCommand invalid = new RegisterContactRequestCommand(id, company, contact, email, consent);

        RuleViolationException ex = assertThrows(RuleViolationException.class, () -> writer.write(invalid));

        assertEquals(code, ex.code());
        assertFalse(repository.existsById(id));
        assertUnchanged(before);
        verifyNoInteractions(referenceGenerator);
    }

    @Test
    void write_referenceCollisionEscapesAndRollsBackWithoutChangingStoredRequest() {
        ContactRequest before = seedRequest();
        String id = UUID.randomUUID().toString();
        when(referenceGenerator.generate()).thenReturn(before.getReceiptReference());

        assertThrows(DataIntegrityViolationException.class, () -> writer.write(command(id)));

        assertFalse(repository.existsById(id));
        assertUnchanged(before);
    }

    private ContactRequest seedRequest() {
        String id = UUID.randomUUID().toString();
        repository.saveAndFlush(ContactRequest.register(id, "Existing Company", "Existing Contact", "existing@example.com", true,
            () -> "CR-AAAAAAAAAA", Clock.fixed(NOW.minusSeconds(60), ZoneOffset.UTC)));
        return repository.findById(id).orElseThrow();
    }

    private void assertUnchanged(ContactRequest before) {
        assertEquals(1L, repository.count());
        ContactRequest after = repository.findById(before.getId()).orElseThrow();
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getReceiptReference(), after.getReceiptReference());
        assertEquals(before.getCompanyName(), after.getCompanyName());
        assertEquals(before.getContactName(), after.getContactName());
        assertEquals(before.getEmail(), after.getEmail());
        assertEquals(before.isConsentGiven(), after.isConsentGiven());
        assertEquals(before.getReceivedAt(), after.getReceivedAt());
    }
}
