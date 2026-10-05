package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyTest {

    // sub-millisecond precision to test truncation
    private static final Instant NOW      = Instant.parse("2030-06-01T12:00:00.000000001Z");
    private static final Clock   FIXED    = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Instant ACTIVATED = Instant.parse("2030-06-01T11:55:00.999999999Z");

    private Emergency base() {
        return Emergency.activateByDriver(
            "550e8400-e29b-41d4-a716-446655440000",
            100L, 10L, 20L, 30L, 40L,
            new GeoPoint(-12.046, -77.042),
            ACTIVATED, FIXED);
    }

    // --- activateByDriver ---

    @Test
    void activateByDriver_setsSourcePriorityStatus() {
        Emergency e = base();
        assertEquals(EmergencySource.DRIVER,     e.getSource());
        assertEquals(EmergencyPriority.CRITICAL,  e.getPriority());
        assertEquals(EmergencyStatus.ACTIVE,      e.getStatus());
    }

    @Test
    void activateByDriver_setsCompanyDriverBusShiftRoute() {
        Emergency e = base();
        assertEquals(100L, e.getCompanyId());
        assertEquals(10L,  e.getDriverId());
        assertEquals(20L,  e.getBusId());
        assertEquals(30L,  e.getShiftId());
        assertEquals(40L,  e.getRouteId());
    }

    @Test
    void activateByDriver_truncatesActivatedAtToMilliseconds() {
        Emergency e = base();
        Instant expected = ACTIVATED.truncatedTo(ChronoUnit.MILLIS);
        assertEquals(expected, e.getActivatedAt());
        assertNotEquals(ACTIVATED, e.getActivatedAt());
    }

    @Test
    void activateByDriver_setsReceivedAtFromClockTruncated() {
        Emergency e = base();
        assertEquals(NOW.truncatedTo(ChronoUnit.MILLIS), e.getReceivedAt());
    }

    @Test
    void activateByDriver_activatedAtAndReceivedAtDiffer() {
        Emergency e = base();
        assertNotEquals(e.getActivatedAt(), e.getReceivedAt());
    }

    @Test
    void activateByDriver_nullPointAllowed() {
        Emergency e = Emergency.activateByDriver(
            "550e8400-e29b-41d4-a716-446655440001",
            100L, 10L, 20L, 30L, 40L, null, ACTIVATED, FIXED);
        assertNull(e.getPoint());
    }

    @Test
    void activateByDriver_storesCompanyId() {
        assertEquals(100L, base().getCompanyId());
    }

    // --- startAttention ---

    @Test
    void startAttention_fromActive_transitionsToInProgress() {
        Emergency e = base();
        Instant at = NOW.plusSeconds(60);
        e.startAttention(999L, at);
        assertEquals(EmergencyStatus.IN_PROGRESS,              e.getStatus());
        assertEquals(999L,                                     e.getResponsibleSupervisorUserId());
        assertEquals(at.truncatedTo(ChronoUnit.MILLIS),        e.getAttentionStartedAt());
    }

    @Test
    void startAttention_fromInProgress_throwsInvalidTransition() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        ConflictException ex = assertThrows(ConflictException.class,
            () -> e.startAttention(888L, NOW.plusSeconds(120)));
        assertEquals("INVALID_TRANSITION", ex.code());
    }

    @Test
    void startAttention_fromClosed_throwsInvalidTransition() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        e.close("outcome", null, NOW.plusSeconds(120));
        ConflictException ex = assertThrows(ConflictException.class,
            () -> e.startAttention(888L, NOW.plusSeconds(180)));
        assertEquals("INVALID_TRANSITION", ex.code());
    }

    // --- close ---

    @Test
    void close_fromActive_throwsAttentionNotStarted() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> base().close("outcome", null, NOW.plusSeconds(120)));
        assertEquals("ATTENTION_NOT_STARTED", ex.code());
    }

    @Test
    void close_fromClosed_throwsInvalidTransition() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        e.close("outcome", null, NOW.plusSeconds(120));
        ConflictException ex = assertThrows(ConflictException.class,
            () -> e.close("outcome2", null, NOW.plusSeconds(180)));
        assertEquals("INVALID_TRANSITION", ex.code());
    }

    @Test
    void close_nullOutcome_throwsOutcomeRequired() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> e.close(null, null, NOW.plusSeconds(120)));
        assertEquals("OUTCOME_REQUIRED", ex.code());
    }

    @Test
    void close_blankOutcome_throwsOutcomeRequired() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> e.close("   ", null, NOW.plusSeconds(120)));
        assertEquals("OUTCOME_REQUIRED", ex.code());
    }

    @Test
    void close_outcomeTooLong_throwsOutcomeTooLong() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> e.close("x".repeat(501), null, NOW.plusSeconds(120)));
        assertEquals("OUTCOME_TOO_LONG", ex.code());
    }

    @Test
    void close_outcome500chars_succeeds() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        assertDoesNotThrow(() -> e.close("x".repeat(500), null, NOW.plusSeconds(120)));
        assertEquals(EmergencyStatus.CLOSED, e.getStatus());
    }

    @Test
    void close_userResponseTooLong_throwsResponseTooLong() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> e.close("valid outcome", "y".repeat(501), NOW.plusSeconds(120)));
        assertEquals("RESPONSE_TOO_LONG", ex.code());
    }

    @Test
    void close_valid_setsAllFields() {
        Emergency e = base();
        Instant closeTime = NOW.plusSeconds(120);
        e.startAttention(999L, NOW.plusSeconds(60));
        e.close("all clear", "response text", closeTime);
        assertEquals(EmergencyStatus.CLOSED,                  e.getStatus());
        assertEquals("all clear",                              e.getOutcome());
        assertEquals("response text",                          e.getUserResponse());
        assertEquals(closeTime.truncatedTo(ChronoUnit.MILLIS), e.getClosedAt());
    }

    // check order: ACTIVE → ATTENTION_NOT_STARTED (not INVALID_TRANSITION or OUTCOME_REQUIRED)
    @Test
    void close_checkOrder_attentionNotStartedBeforeClosed() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> base().close("outcome", null, NOW.plusSeconds(60)));
        assertEquals("ATTENTION_NOT_STARTED", ex.code());
    }

    // check order: CLOSED → INVALID_TRANSITION (not OUTCOME_REQUIRED)
    @Test
    void close_checkOrder_closedBeforeOutcomeRequired() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        e.close("ok", null, NOW.plusSeconds(120));
        ConflictException ex = assertThrows(ConflictException.class,
            () -> e.close(null, null, NOW.plusSeconds(180)));
        assertEquals("INVALID_TRANSITION", ex.code());
    }

    // check order: OUTCOME_REQUIRED before OUTCOME_TOO_LONG
    @Test
    void close_checkOrder_outcomeRequiredBeforeTooLong() {
        Emergency e = base();
        e.startAttention(999L, NOW.plusSeconds(60));
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> e.close(null, null, NOW.plusSeconds(120)));
        assertEquals("OUTCOME_REQUIRED", ex.code());
    }

    // --- hasSamePayloadAs ---

    @Test
    void hasSamePayloadAs_identical_returnsTrue() {
        assertTrue(base().hasSamePayloadAs(10L, 30L, ACTIVATED, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentDriver_returnsFalse() {
        assertFalse(base().hasSamePayloadAs(99L, 30L, ACTIVATED, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentShift_returnsFalse() {
        assertFalse(base().hasSamePayloadAs(10L, 99L, ACTIVATED, -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentActivatedAt_returnsFalse() {
        assertFalse(base().hasSamePayloadAs(10L, 30L, ACTIVATED.plusSeconds(1), -12.046, -77.042));
    }

    @Test
    void hasSamePayloadAs_differentCoords_returnsFalse() {
        assertFalse(base().hasSamePayloadAs(10L, 30L, ACTIVATED, -12.0, -77.0));
    }

    @Test
    void hasSamePayloadAs_bothNullMatchesNullPoint() {
        Emergency e = Emergency.activateByDriver(
            "550e8400-e29b-41d4-a716-446655440002",
            100L, 10L, 20L, 30L, 40L, null, ACTIVATED, FIXED);
        assertTrue(e.hasSamePayloadAs(10L, 30L, ACTIVATED, null, null));
    }

    @Test
    void hasSamePayloadAs_bothNullButPointExists_returnsFalse() {
        assertFalse(base().hasSamePayloadAs(10L, 30L, ACTIVATED, null, null));
    }

    @Test
    void hasSamePayloadAs_activatedAtTruncatedSameAsOriginal() {
        // incoming with sub-millisecond precision must still match after truncation
        assertTrue(base().hasSamePayloadAs(10L, 30L, ACTIVATED, -12.046, -77.042));
        Instant sameMillis = ACTIVATED.truncatedTo(ChronoUnit.MILLIS);
        assertTrue(base().hasSamePayloadAs(10L, 30L, sameMillis, -12.046, -77.042));
    }
}
