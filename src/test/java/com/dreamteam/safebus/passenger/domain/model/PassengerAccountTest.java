package com.dreamteam.safebus.passenger.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PassengerAccountTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void register_valid8DigitDni_createsAccount() {
        PassengerAccount a = PassengerAccount.register(1L, "12345678", 10L, "2026-10", CLOCK);
        assertEquals("12345678", a.getDni());
        assertEquals(1L, a.getUserAccountId());
        assertEquals(10L, a.getFaceImageId());
        assertEquals("2026-10", a.getTermsVersion());
        assertNotNull(a.getTermsAcceptedAt());
        assertNotNull(a.getCreatedAt());
    }

    @Test
    void register_dnWithLeadingZeros_accepted() {
        assertDoesNotThrow(() -> PassengerAccount.register(1L, "00123456", 10L, "v1", CLOCK));
    }

    @Test
    void register_dniWithSurroundingSpaces_trimmedAndAccepted() {
        PassengerAccount a = PassengerAccount.register(1L, "  12345678  ", 10L, "v1", CLOCK);
        assertEquals("12345678", a.getDni());
    }

    @Test
    void register_7DigitDni_throwsInvalidDni() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "1234567", 10L, "v1", CLOCK));
        assertEquals("INVALID_DNI", ex.code());
    }

    @Test
    void register_9DigitDni_throwsInvalidDni() {
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "123456789", 10L, "v1", CLOCK));
    }

    @Test
    void register_dniWithLetters_throwsInvalidDni() {
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "1234567A", 10L, "v1", CLOCK));
    }

    @Test
    void register_arabicIndicDigits_throwsInvalidDni() {
        // Arabic-Indic digit ONE through EIGHT: ١٢٣٤٥٦٧٨
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "١٢٣٤٥٦٧٨", 10L, "v1", CLOCK));
    }

    @Test
    void register_nullDni_throwsInvalidDni() {
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, null, 10L, "v1", CLOCK));
    }

    @Test
    void register_emptyDni_throwsInvalidDni() {
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "", 10L, "v1", CLOCK));
    }

    @Test
    void register_dniWithSpacesOnlyAfterTrim_throwsInvalidDni() {
        assertThrows(RuleViolationException.class,
            () -> PassengerAccount.register(1L, "   ", 10L, "v1", CLOCK));
    }
}
