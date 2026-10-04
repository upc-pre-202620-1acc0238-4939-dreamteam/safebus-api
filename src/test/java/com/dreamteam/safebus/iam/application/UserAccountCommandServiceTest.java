package com.dreamteam.safebus.iam.application;

import com.dreamteam.safebus.iam.domain.model.UserRole;
import com.dreamteam.safebus.iam.domain.repository.UserAccountRepository;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserAccountCommandServiceTest {

    @Autowired
    private UserAccountCommandService service;

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createSupervisor_storesBcryptHash() {
        Long id = service.createSupervisor("sup-test", "password123", 1L);
        String hash = repository.findById(id).orElseThrow().getPasswordHash();
        assertTrue(hash.startsWith("$2"), "Password must be stored as a BCrypt hash");
        assertTrue(passwordEncoder.matches("password123", hash));
    }

    @Test
    void createPassenger_passwordNeverStoredInClear() {
        Long id = service.createPassenger("pax-test", "password123");
        String hash = repository.findById(id).orElseThrow().getPasswordHash();
        org.junit.jupiter.api.Assertions.assertNotEquals("password123", hash);
    }

    @Test
    void createAccount_passwordTooShort_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> service.createPassenger("pax-short", "abc"));
        assertEquals("PASSWORD_TOO_SHORT", ex.code());
    }

    @Test
    void createAccount_passwordTooLong_throws() {
        String tooLong = "a".repeat(73);
        RuleViolationException ex = assertThrows(RuleViolationException.class,
                () -> service.createPassenger("pax-long", tooLong));
        assertEquals("PASSWORD_TOO_LONG", ex.code());
    }

    @Test
    void createAccount_duplicateLoginId_throws() {
        service.createPassenger("pax-dup", "password123");
        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.createPassenger("pax-dup", "password456"));
        assertEquals("LOGIN_ID_TAKEN", ex.code());
    }

    @Test
    void createSupervisor_savesCorrectRole() {
        Long id = service.createSupervisor("sup-role", "password123", 2L);
        assertEquals(UserRole.SUPERVISOR, repository.findById(id).orElseThrow().getRole());
    }

    @Test
    void createDriver_savesCorrectCompanyId() {
        Long id = service.createDriver("drv-company", "password123", 5L);
        assertNotNull(repository.findById(id).orElseThrow().getCompanyId());
        assertEquals(5L, repository.findById(id).orElseThrow().getCompanyId());
    }
}
