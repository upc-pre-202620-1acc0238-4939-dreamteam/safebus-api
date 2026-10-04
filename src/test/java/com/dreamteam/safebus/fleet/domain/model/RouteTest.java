package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteTest {

    @Test
    void create_valid_storesFieldsAndEnabled() {
        Route r = Route.create(1L, "Route A", "City A", "City B");
        assertEquals("Route A", r.getName());
        assertEquals("City A", r.getOrigin());
        assertEquals("City B", r.getDestination());
        assertTrue(r.isEnabled());
    }

    @Test
    void create_fieldsAreTrimmed() {
        Route r = Route.create(1L, " Route A ", " City A ", " City B ");
        assertEquals("Route A", r.getName());
        assertEquals("City A", r.getOrigin());
        assertEquals("City B", r.getDestination());
    }

    @Test
    void create_blankName_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Route.create(1L, "  ", "A", "B"));
        assertEquals("INVALID_NAME", ex.code());
    }

    @Test
    void create_nullOrigin_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Route.create(1L, "Route", null, "B"));
        assertEquals("INVALID_ORIGIN", ex.code());
    }

    @Test
    void create_blankDestination_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> Route.create(1L, "Route", "A", "   "));
        assertEquals("INVALID_DESTINATION", ex.code());
    }
}
