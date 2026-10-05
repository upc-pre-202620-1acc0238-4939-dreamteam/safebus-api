package com.dreamteam.safebus.contact.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class ContactRequestTest {

    private static final String ID = "12345678-1234-1234-1234-123456789abc";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);

    private ContactRequest register(String company, String contact, String email, boolean consent) {
        return ContactRequest.register(ID, company, contact, email, consent, () -> "CR-012345ABCD", CLOCK);
    }

    @Test
    void register_trimsFieldsAndPreservesAssignedIdAndEmailCase() {
        ContactRequest request = register("  Safe Bus  ", "  Ana Perez  ", "  Ana@Example.com  ", true);

        assertEquals(ID, request.getId());
        assertTrue(request.isNew());
        assertEquals("CR-012345ABCD", request.getReceiptReference());
        assertEquals("Safe Bus", request.getCompanyName());
        assertEquals("Ana Perez", request.getContactName());
        assertEquals("Ana@Example.com", request.getEmail());
        assertTrue(request.isConsentGiven());
        assertEquals(Instant.parse("2030-01-01T10:00:00.123Z"), request.getReceivedAt());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void register_nameBoundaryLengthsAreValid(int length) {
        String name = "A".repeat(length);
        ContactRequest request = register(" " + name + " ", " " + name + " ", "a@example.com", true);
        assertEquals(name, request.getCompanyName());
        assertEquals(name, request.getContactName());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void register_missingCompanyNameIsRejected(String name) {
        assertCode("INVALID_COMPANY_NAME", () -> register(name, "Ana", "a@example.com", true));
    }

    @Test
    void register_companyNameOf101CharactersIsRejected() {
        assertCode("INVALID_COMPANY_NAME", () -> register("A".repeat(101), "Ana", "a@example.com", true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void register_missingContactNameIsRejected(String name) {
        assertCode("INVALID_CONTACT_NAME", () -> register("Safe Bus", name, "a@example.com", true));
    }

    @Test
    void register_contactNameOf101CharactersIsRejected() {
        assertCode("INVALID_CONTACT_NAME", () -> register("Safe Bus", "A".repeat(101), "a@example.com", true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "example.com", "a@@example.com", "a@example", "@example.com", "a@",
        "a@exam ple.com", "a b@example.com", "a@exam\tple.com", "a@exam\nple.com"})
    void register_invalidEmailIsRejected(String email) {
        assertCode("INVALID_EMAIL", () -> register("Safe Bus", "Ana", email, true));
    }

    @Test
    void register_emailOf255CharactersIsRejected() {
        assertCode("INVALID_EMAIL", () -> register("Safe Bus", "Ana", "a".repeat(249) + "@b.com", true));
    }

    @Test
    void register_emailOf254CharactersIsValid() {
        String email = "a".repeat(248) + "@b.com";
        assertEquals(email, register("Safe Bus", "Ana", email, true).getEmail());
    }

    @Test
    void register_withoutConsentIsRejected() {
        assertCode("CONSENT_REQUIRED", () -> register("Safe Bus", "Ana", "a@example.com", false));
    }

    @Test
    void samePayload_comparesTrimmedNamesAndCaseInsensitiveEmail() {
        ContactRequest request = register("Safe Bus", "Ana", "Ana@Example.com", true);
        assertTrue(request.hasSamePayloadAs(" Safe Bus ", " Ana ", " ANA@EXAMPLE.COM ", true));
        assertFalse(request.hasSamePayloadAs("Other", "Ana", "Ana@Example.com", true));
        assertFalse(request.hasSamePayloadAs("Safe Bus", "Other", "Ana@Example.com", true));
        assertFalse(request.hasSamePayloadAs("Safe Bus", "Ana", "other@example.com", true));
        assertFalse(request.hasSamePayloadAs("Safe Bus", "Ana", "Ana@Example.com", false));
        assertFalse(request.hasSamePayloadAs(null, "Ana", "Ana@Example.com", true));
        assertFalse(request.hasSamePayloadAs("Safe Bus", null, "Ana@Example.com", true));
        assertFalse(request.hasSamePayloadAs("Safe Bus", "Ana", null, true));
    }

    private void assertCode(String code, org.junit.jupiter.api.function.Executable operation) {
        RuleViolationException ex = assertThrows(RuleViolationException.class, operation);
        assertEquals(code, ex.code());
    }
}
