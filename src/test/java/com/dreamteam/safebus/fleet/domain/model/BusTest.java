package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.fleet.domain.port.QrCodeGenerator;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BusTest {

    private static final QrCodeGenerator FIXED_QR = () -> "test-qr-code";

    @Test
    void create_validPlate_normalizedAndEnabled() {
        Bus b = Bus.create(1L, " abc-123 ", FIXED_QR);
        assertEquals("ABC-123", b.getPlate());
        assertTrue(b.isEnabled());
        assertEquals("test-qr-code", b.getQrCode());
    }

    @Test
    void create_lowerCasePlate_uppercased() {
        Bus b = Bus.create(1L, "xyz-999", FIXED_QR);
        assertEquals("XYZ-999", b.getPlate());
    }

    @Test
    void create_blankPlate_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Bus.create(1L, "   ", FIXED_QR));
        assertEquals("INVALID_PLATE", ex.code());
    }

    @Test
    void create_nullPlate_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Bus.create(1L, null, FIXED_QR));
        assertEquals("INVALID_PLATE", ex.code());
    }

    @Test
    void create_plateTooLong_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Bus.create(1L, "A".repeat(16), FIXED_QR));
        assertEquals("INVALID_PLATE", ex.code());
    }

    @Test
    void create_maxLengthPlate_succeeds() {
        Bus b = Bus.create(1L, "A".repeat(15), FIXED_QR);
        assertEquals(15, b.getPlate().length());
    }
}
