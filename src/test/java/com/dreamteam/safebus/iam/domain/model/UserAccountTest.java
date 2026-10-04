package com.dreamteam.safebus.iam.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserAccountTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
    private static final String HASH = "$2a$10$somehashedpasswordvalue1234567890";

    @Test
    void create_supervisor_requiresCompanyId() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create("sup-001", HASH, UserRole.SUPERVISOR, null, FIXED_CLOCK));
        assertEquals("COMPANY_REQUIRED", ex.code());
    }

    @Test
    void create_driver_requiresCompanyId() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create("drv-001", HASH, UserRole.DRIVER, null, FIXED_CLOCK));
        assertEquals("COMPANY_REQUIRED", ex.code());
    }

    @Test
    void create_passenger_mustNotHaveCompanyId() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create("pax-001", HASH, UserRole.PASSENGER, 1L, FIXED_CLOCK));
        assertEquals("COMPANY_FORBIDDEN", ex.code());
    }

    @Test
    void create_blankLoginId_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create("  ", HASH, UserRole.PASSENGER, null, FIXED_CLOCK));
        assertEquals("INVALID_LOGIN_ID", ex.code());
    }

    @Test
    void create_loginIdTooLong_throws() {
        String tooLong = "a".repeat(51);
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create(tooLong, HASH, UserRole.PASSENGER, null, FIXED_CLOCK));
        assertEquals("INVALID_LOGIN_ID", ex.code());
    }

    @Test
    void create_blankPasswordHash_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> UserAccount.create("pax-001", "  ", UserRole.PASSENGER, null, FIXED_CLOCK));
        assertEquals("INVALID_PASSWORD_HASH", ex.code());
    }

    @Test
    void create_validSupervisor_setsFieldsCorrectly() {
        UserAccount account = UserAccount.create("sup-001", HASH, UserRole.SUPERVISOR, 1L, FIXED_CLOCK);
        assertEquals("sup-001", account.getLoginId());
        assertEquals(UserRole.SUPERVISOR, account.getRole());
        assertEquals(1L, account.getCompanyId());
        assertTrue(account.isEnabled());
        assertEquals(FIXED_CLOCK.instant(), account.getCreatedAt());
        assertNotNull(account.getPasswordHash());
    }

    @Test
    void create_loginIdIsTrimmed() {
        UserAccount account = UserAccount.create("  sup-001  ", HASH, UserRole.SUPERVISOR, 1L, FIXED_CLOCK);
        assertEquals("sup-001", account.getLoginId());
    }

    @Test
    void create_loginIdIsLowercased() {
        UserAccount account = UserAccount.create("SUP-Mixed", HASH, UserRole.SUPERVISOR, 1L, FIXED_CLOCK);
        assertEquals("sup-mixed", account.getLoginId());
    }

    @Test
    void disable_setsEnabledFalse() {
        UserAccount account = UserAccount.create("sup-001", HASH, UserRole.SUPERVISOR, 1L, FIXED_CLOCK);
        account.disable();
        org.junit.jupiter.api.Assertions.assertFalse(account.isEnabled());
    }

    @Test
    void enable_setsEnabledTrue() {
        UserAccount account = UserAccount.create("sup-001", HASH, UserRole.SUPERVISOR, 1L, FIXED_CLOCK);
        account.disable();
        account.enable();
        assertTrue(account.isEnabled());
    }
}
