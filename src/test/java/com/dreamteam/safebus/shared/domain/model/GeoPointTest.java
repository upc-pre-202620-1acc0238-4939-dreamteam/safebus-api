package com.dreamteam.safebus.shared.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeoPointTest {

    @Test
    void latitude_90_isValid() {
        assertDoesNotThrow(() -> new GeoPoint(90.0, 0.0));
    }

    @Test
    void latitude_minus90_isValid() {
        assertDoesNotThrow(() -> new GeoPoint(-90.0, 0.0));
    }

    @Test
    void longitude_180_isValid() {
        assertDoesNotThrow(() -> new GeoPoint(0.0, 180.0));
    }

    @Test
    void longitude_minus180_isValid() {
        assertDoesNotThrow(() -> new GeoPoint(0.0, -180.0));
    }

    @Test
    void latitude_justAbove90_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(90.1, 0.0));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void latitude_justBelow_minus90_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(-90.1, 0.0));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void longitude_justAbove180_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(0.0, 180.1));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void longitude_justBelow_minus180_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(0.0, -180.1));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void latitude_NaN_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(Double.NaN, 0.0));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void longitude_NaN_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(0.0, Double.NaN));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void latitude_positiveInfinity_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(Double.POSITIVE_INFINITY, 0.0));
        assertEquals("INVALID_COORDINATES", ex.code());
    }

    @Test
    void longitude_negativeInfinity_throwsInvalidCoordinates() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> new GeoPoint(0.0, Double.NEGATIVE_INFINITY));
        assertEquals("INVALID_COORDINATES", ex.code());
    }
}
