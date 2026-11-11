package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountingSourceTest {

    private static final Instant NOW = Instant.parse("2030-03-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Clock LATER = Clock.fixed(NOW.plusSeconds(600), ZoneOffset.UTC);

    private CountingSource source() {
        return CountingSource.register(1L, 2L, "  front door  ", "hash-1", 9L, CLOCK);
    }

    @Test
    void registerCreatesAnActiveSourceWithTrimmedLabel() {
        CountingSource s = source();

        assertEquals(CountingSourceStatus.ACTIVE, s.getStatus());
        assertEquals(1, s.getActiveMarker());
        assertEquals("front door", s.getLabel());
        assertEquals(NOW, s.getCreatedAt());
        assertEquals(2L, s.getBusId());
        assertEquals(9L, s.getCreatedByUserId());
        assertNull(s.getRotatedAt());
        assertNull(s.getRevokedAt());
    }

    @Test
    void blankLabelIsStoredAsNull() {
        assertNull(CountingSource.register(1L, 2L, "   ", "h", 9L, CLOCK).getLabel());
        assertNull(CountingSource.register(1L, 2L, null, "h", 9L, CLOCK).getLabel());
    }

    @Test
    void labelOverSixtyCharactersIsRejected() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> CountingSource.register(1L, 2L, "x".repeat(61), "h", 9L, CLOCK));

        assertEquals("LABEL_TOO_LONG", ex.code());
        assertEquals(60, CountingSource.register(1L, 2L, "x".repeat(60), "h", 9L, CLOCK).getLabel().length());
    }

    @Test
    void rotateReplacesTheHashAndKeepsTheSourceActive() {
        CountingSource s = source();

        s.rotate("hash-2", LATER);

        assertTrue(s.isActive());
        assertEquals(NOW.plusSeconds(600), s.getRotatedAt());
    }

    @Test
    void revokeDeactivatesAndFreesTheActiveMarker() {
        CountingSource s = source();

        assertTrue(s.revoke(LATER));

        assertEquals(CountingSourceStatus.REVOKED, s.getStatus());
        assertNull(s.getActiveMarker());
        assertEquals(NOW.plusSeconds(600), s.getRevokedAt());
    }

    @Test
    void revokingTwiceChangesNothing() {
        CountingSource s = source();
        s.revoke(LATER);

        assertFalse(s.revoke(Clock.fixed(NOW.plusSeconds(900), ZoneOffset.UTC)));

        assertEquals(NOW.plusSeconds(600), s.getRevokedAt());
    }

    @Test
    void aRevokedSourceCannotBeRotated() {
        CountingSource s = source();
        s.revoke(LATER);

        ConflictException ex = assertThrows(ConflictException.class, () -> s.rotate("hash-2", LATER));

        assertEquals("SOURCE_REVOKED", ex.code());
    }
}
