package com.dreamteam.safebus.passenger.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PassengerJourneyEndTest {

    private static final Instant START = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant NOW = Instant.parse("2026-01-01T13:00:00Z");
    private static final Clock START_CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    private PassengerJourney journey() {
        return PassengerJourney.start(1L, 2L, 3L, START_CLOCK);
    }

    private String codeOf(RuleViolationException ex) {
        return ex.code();
    }

    @Test
    void noReportedTimeUsesTheServerTime() {
        PassengerJourney j = journey();
        assertTrue(j.end(JourneyEndReason.MANUAL, null, null, NOW));
        assertEquals(NOW, j.getEndedAt());
    }

    @Test
    void reportedPastTimeIsStoredAsGiven() {
        PassengerJourney j = journey();
        Instant reported = Instant.parse("2026-01-01T12:30:00Z");
        j.end(JourneyEndReason.MANUAL, reported, null, NOW);
        assertEquals(reported, j.getEndedAt());
    }

    @Test
    void reportedTimeEqualToTheStartIsAccepted() {
        PassengerJourney j = journey();
        j.end(JourneyEndReason.MANUAL, START, null, NOW);
        assertEquals(START, j.getEndedAt());
    }

    @Test
    void reportedTimeBeforeTheStartIsRejectedAndTheJourneyStaysActive() {
        PassengerJourney j = journey();
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> j.end(JourneyEndReason.MANUAL, START.minusMillis(1), null, NOW));
        assertEquals("INVALID_END_TIME", codeOf(ex));
        assertEquals(JourneyStatus.ACTIVE, j.getStatus());
        assertEquals(Integer.valueOf(1), j.getActiveMarker());
    }

    @Test
    void reportedTimeWithinTheSkewIsClampedToTheServerTime() {
        PassengerJourney j = journey();
        j.end(JourneyEndReason.MANUAL, NOW.plusSeconds(300), null, NOW);
        assertEquals(NOW, j.getEndedAt());
    }

    @Test
    void reportedTimeBeyondTheSkewIsRejected() {
        PassengerJourney j = journey();
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> j.end(JourneyEndReason.MANUAL, NOW.plusSeconds(301), null, NOW));
        assertEquals("INVALID_END_TIME", codeOf(ex));
        assertEquals(JourneyStatus.ACTIVE, j.getStatus());
    }

    @Test
    void automaticSeparationStoresOnlyTheSummary() {
        PassengerJourney j = journey();
        j.end(JourneyEndReason.AUTOMATIC_SEPARATION, NOW, new SeparationSummary(130, 65), NOW);
        assertEquals(JourneyEndReason.AUTOMATIC_SEPARATION, j.getEndReason());
        assertEquals(new SeparationSummary(130, 65), j.getSeparationSummary());
    }

    @Test
    void automaticSeparationWithoutSummaryIsAllowed() {
        PassengerJourney j = journey();
        j.end(JourneyEndReason.AUTOMATIC_SEPARATION, null, null, NOW);
        assertNull(j.getSeparationSummary());
    }

    @Test
    void summaryWithAnotherReasonIsRejected() {
        PassengerJourney j = journey();
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> j.end(JourneyEndReason.MANUAL, null, new SeparationSummary(130, 65), NOW));
        assertEquals("INVALID_END_SUMMARY", codeOf(ex));
        assertEquals(JourneyStatus.ACTIVE, j.getStatus());
    }

    @Test
    void summaryDurationLongerThanTheJourneyIsRejected() {
        PassengerJourney j = journey();
        Instant reported = START.plusSeconds(60);
        j.end(JourneyEndReason.AUTOMATIC_SEPARATION, reported, new SeparationSummary(130, 60), NOW);
        PassengerJourney other = journey();
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> other.end(JourneyEndReason.AUTOMATIC_SEPARATION, reported, new SeparationSummary(130, 61), NOW));
        assertEquals("INVALID_END_SUMMARY", codeOf(ex));
    }

    @Test
    void summaryRequiresBothPositiveValues() {
        assertEquals("INVALID_END_SUMMARY",
            assertThrows(RuleViolationException.class, () -> SeparationSummary.ofNullable(130, null)).code());
        assertEquals("INVALID_END_SUMMARY",
            assertThrows(RuleViolationException.class, () -> SeparationSummary.ofNullable(null, 65)).code());
        assertEquals("INVALID_END_SUMMARY",
            assertThrows(RuleViolationException.class, () -> SeparationSummary.ofNullable(0, 65)).code());
        assertEquals("INVALID_END_SUMMARY",
            assertThrows(RuleViolationException.class, () -> SeparationSummary.ofNullable(130, -1)).code());
        assertNull(SeparationSummary.ofNullable(null, null));
    }

    @Test
    void replayChangesNothingEvenWithDifferentReasonTimeAndSummary() {
        PassengerJourney j = journey();
        Instant first = Instant.parse("2026-01-01T12:30:00Z");
        j.end(JourneyEndReason.AUTOMATIC_SEPARATION, first, new SeparationSummary(130, 65), NOW);

        boolean changed = j.end(JourneyEndReason.MANUAL, Instant.parse("2026-01-01T12:45:00Z"),
            new SeparationSummary(999, 10), NOW.plusSeconds(60));

        assertFalse(changed);
        assertEquals(JourneyEndReason.AUTOMATIC_SEPARATION, j.getEndReason());
        assertEquals(first, j.getEndedAt());
        assertEquals(new SeparationSummary(130, 65), j.getSeparationSummary());
    }

    @Test
    void replayIgnoresAnInvalidTimeBecauseTheOriginalDataWins() {
        PassengerJourney j = journey();
        j.end(JourneyEndReason.MANUAL, null, null, NOW);
        assertFalse(j.end(JourneyEndReason.MANUAL, START.minusSeconds(10), null, NOW));
        assertEquals(NOW, j.getEndedAt());
    }
}
