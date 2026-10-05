package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.fleet.domain.port.CredentialGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverTest {

    private static final Instant FIXED_NOW = Instant.parse("2025-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
    private static final Duration TTL = Duration.ofDays(365);

    private int callCount = 0;
    private final CredentialGenerator COUNTER_GEN = () -> "cred-" + (++callCount);

    @Test
    void create_validDriver_setsExpiryFromClockPlusTtl() {
        Driver d = Driver.create(1L, 10L, "John Doe", COUNTER_GEN, TTL, CLOCK);
        assertEquals(FIXED_NOW.plus(TTL), d.getQrCredentialExpiresAt());
        assertTrue(d.isEnabled());
        assertEquals("John Doe", d.getFullName());
    }

    @Test
    void create_fullNameTrimmed() {
        Driver d = Driver.create(1L, 10L, "  Jane Doe  ", COUNTER_GEN, TTL, CLOCK);
        assertEquals("Jane Doe", d.getFullName());
    }

    @Test
    void create_blankFullName_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Driver.create(1L, 10L, "  ", COUNTER_GEN, TTL, CLOCK));
        assertEquals("INVALID_FULL_NAME", ex.code());
    }

    @Test
    void create_nullFullName_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Driver.create(1L, 10L, null, COUNTER_GEN, TTL, CLOCK));
        assertEquals("INVALID_FULL_NAME", ex.code());
    }

    @Test
    void create_twoDrivers_haveDifferentCredentials() {
        Driver d1 = Driver.create(1L, 10L, "Driver One", COUNTER_GEN, TTL, CLOCK);
        Driver d2 = Driver.create(1L, 11L, "Driver Two", COUNTER_GEN, TTL, CLOCK);
        assertNotEquals(d1.getQrCredential(), d2.getQrCredential());
    }

    @Test
    void create_differentTtl_differentExpiry() {
        Duration shortTtl = Duration.ofDays(30);
        Driver d1 = Driver.create(1L, 10L, "Driver One", COUNTER_GEN, TTL, CLOCK);
        Driver d2 = Driver.create(1L, 11L, "Driver Two", COUNTER_GEN, shortTtl, CLOCK);
        assertNotEquals(d1.getQrCredentialExpiresAt(), d2.getQrCredentialExpiresAt());
    }
}
