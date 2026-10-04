package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompanyTest {

    @Test
    void create_validName_succeeds() {
        Company c = Company.create("Demo Transport");
        assertEquals("Demo Transport", c.getName());
    }

    @Test
    void create_nameTrimmed() {
        Company c = Company.create("  Demo Transport  ");
        assertEquals("Demo Transport", c.getName());
    }

    @Test
    void create_blankName_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Company.create("   "));
        assertEquals("INVALID_NAME", ex.code());
    }

    @Test
    void create_nullName_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Company.create(null));
        assertEquals("INVALID_NAME", ex.code());
    }

    @Test
    void create_nameTooLong_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Company.create("A".repeat(101)));
        assertEquals("INVALID_NAME", ex.code());
    }

    @Test
    void create_maxLengthName_succeeds() {
        Company c = Company.create("A".repeat(100));
        assertEquals(100, c.getName().length());
    }
}
