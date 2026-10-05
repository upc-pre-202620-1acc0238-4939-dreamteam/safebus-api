package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShiftAssignmentTest {

    private static final Instant T1 = Instant.parse("2025-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2025-01-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2025-01-01T12:00:00Z");
    private static final Instant T4 = Instant.parse("2025-01-01T14:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(T1, ZoneOffset.UTC);

    @Test
    void create_validPeriod_storesFieldsAndStatus() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        assertEquals(AssignmentStatus.ASSIGNED, sa.getStatus());
        assertEquals(T1, sa.getCreatedAt());
        assertEquals(99L, sa.getCreatedByUserId());
        assertEquals(T1, sa.getPlannedStart());
        assertEquals(T2, sa.getPlannedEnd());
    }

    @Test
    void create_endEqualToStart_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> ShiftAssignment.create(1L, 2L, 3L, T1, T1, 99L, FIXED_CLOCK));
        assertEquals("INVALID_PERIOD", ex.code());
    }

    @Test
    void create_endBeforeStart_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> ShiftAssignment.create(1L, 2L, 3L, T2, T1, 99L, FIXED_CLOCK));
        assertEquals("INVALID_PERIOD", ex.code());
    }

    @Test
    void create_nullStart_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> ShiftAssignment.create(1L, 2L, 3L, null, T2, 99L, FIXED_CLOCK));
        assertEquals("INVALID_PERIOD", ex.code());
    }

    @Test
    void create_nullEnd_throws() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> ShiftAssignment.create(1L, 2L, 3L, T1, null, 99L, FIXED_CLOCK));
        assertEquals("INVALID_PERIOD", ex.code());
    }

    @Test
    void activate_fromAssigned_becomesActive() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        sa.activate();
        assertEquals(AssignmentStatus.ACTIVE, sa.getStatus());
    }

    @Test
    void activate_fromActive_throwsAssignmentNotAvailable() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        sa.activate();
        RuleViolationException ex = assertThrows(RuleViolationException.class, sa::activate);
        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
    }

    @Test
    void activate_fromClosed_throwsAssignmentNotAvailable() throws Exception {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        var field = ShiftAssignment.class.getDeclaredField("status");
        field.setAccessible(true);
        field.set(sa, AssignmentStatus.CLOSED);
        RuleViolationException ex = assertThrows(RuleViolationException.class, sa::activate);
        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
    }

    @Test
    void overlaps_identicalPeriods_true() {
        assertTrue(ShiftAssignment.overlaps(T1, T2, T1, T2));
    }

    @Test
    void overlaps_partialOverlap_true() {
        assertTrue(ShiftAssignment.overlaps(T1, T3, T2, T4));
    }

    @Test
    void overlaps_nested_true() {
        assertTrue(ShiftAssignment.overlaps(T1, T4, T2, T3));
    }

    @Test
    void overlaps_contiguous_false() {
        assertFalse(ShiftAssignment.overlaps(T1, T2, T2, T3));
    }

    @Test
    void overlaps_disjoint_false() {
        assertFalse(ShiftAssignment.overlaps(T1, T2, T3, T4));
    }

    @Test
    void close_active_becomesClosedAndReturnsTrue() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        sa.activate();

        assertTrue(sa.close());

        assertEquals(AssignmentStatus.CLOSED, sa.getStatus());
    }

    @Test
    void close_alreadyClosed_returnsFalseAndStaysClosed() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);
        sa.activate();
        sa.close();

        assertFalse(sa.close());

        assertEquals(AssignmentStatus.CLOSED, sa.getStatus());
    }

    @Test
    void close_assigned_throwsConflictAssignmentNotActive() {
        ShiftAssignment sa = ShiftAssignment.create(1L, 2L, 3L, T1, T2, 99L, FIXED_CLOCK);

        ConflictException ex = assertThrows(ConflictException.class, sa::close);

        assertEquals("ASSIGNMENT_NOT_ACTIVE", ex.code());
        assertEquals(AssignmentStatus.ASSIGNED, sa.getStatus());
    }
}
