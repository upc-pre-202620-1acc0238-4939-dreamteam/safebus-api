package com.dreamteam.safebus.contact.domain.repository;

import com.dreamteam.safebus.contact.domain.model.ContactRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ContactRequestRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);

    @Autowired ContactRequestRepository repository;

    @AfterEach
    void cleanUp() {
        repository.deleteAll();
    }

    @Test
    void saveAndFlush_assignedIdIsInsertedAndLoadedAsNotNew() {
        String id = UUID.randomUUID().toString();
        ContactRequest request = ContactRequest.register(id, "Safe Bus", "Ana", "Ana@example.com", true,
            () -> "CR-012345ABCD", CLOCK);
        assertTrue(request.isNew());

        repository.saveAndFlush(request);

        ContactRequest stored = repository.findById(id).orElseThrow();
        assertFalse(stored.isNew());
        assertAllFieldsEqual(request, stored);
        assertEquals(1L, repository.count());
    }

    @Test
    void saveAndFlush_sameAssignedIdRejectsInsertWithoutOverwritingAnyField() {
        String id = UUID.randomUUID().toString();
        repository.saveAndFlush(ContactRequest.register(id, "Safe Bus", "Ana", "Ana@example.com", true,
            () -> "CR-012345ABCD", CLOCK));
        ContactRequest before = repository.findById(id).orElseThrow();
        ContactRequest replacement = ContactRequest.register(id, "Other Company", "Other Contact", "other@example.com", true,
            () -> "CR-987654ABCD", Clock.offset(CLOCK, java.time.Duration.ofDays(1)));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(replacement));

        assertEquals(1L, repository.count());
        assertAllFieldsEqual(before, repository.findById(id).orElseThrow());
    }

    @Test
    void saveAndFlush_duplicateReceiptReferenceRejectsInsertAndPreservesStoredRow() {
        String id = UUID.randomUUID().toString();
        repository.saveAndFlush(ContactRequest.register(id, "Safe Bus", "Ana", "Ana@example.com", true,
            () -> "CR-012345ABCD", CLOCK));
        ContactRequest before = repository.findById(id).orElseThrow();
        String otherId = UUID.randomUUID().toString();
        ContactRequest collision = ContactRequest.register(otherId, "Other Company", "Other Contact", "other@example.com", true,
            () -> "CR-012345ABCD", CLOCK);

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(collision));

        assertEquals(1L, repository.count());
        assertFalse(repository.existsById(otherId));
        assertAllFieldsEqual(before, repository.findById(id).orElseThrow());
    }

    private void assertAllFieldsEqual(ContactRequest expected, ContactRequest actual) {
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getReceiptReference(), actual.getReceiptReference());
        assertEquals(expected.getCompanyName(), actual.getCompanyName());
        assertEquals(expected.getContactName(), actual.getContactName());
        assertEquals(expected.getEmail(), actual.getEmail());
        assertEquals(expected.isConsentGiven(), actual.isConsentGiven());
        assertEquals(expected.getReceivedAt(), actual.getReceivedAt());
    }
}
