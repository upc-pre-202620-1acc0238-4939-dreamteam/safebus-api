package com.dreamteam.safebus.safetycase.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PassengerRequestGroupTest {

    private static final Instant NOW = Instant.parse("2030-03-01T10:00:00.123456Z");

    private PassengerRequestGroup open() {
        return PassengerRequestGroup.open(1L, 2L, 3L, NOW);
    }

    @Test
    void open_startsCollectingWithAFiveMinuteWindowAndAnOpenMarker() {
        PassengerRequestGroup g = open();

        assertEquals(GroupStatus.COLLECTING, g.getStatus());
        assertEquals(1L, g.getCompanyId());
        assertEquals(2L, g.getBusId());
        assertEquals(3L, g.getShiftId());
        assertEquals(1, g.getOpenMarker());
        assertNull(g.getAwaitingSince());
        assertEquals(g.getWindowStartedAt().plus(5, ChronoUnit.MINUTES), g.getWindowExpiresAt());
    }

    @Test
    void open_truncatesTheWindowStartToMilliseconds() {
        assertEquals(Instant.parse("2030-03-01T10:00:00.123Z"), open().getWindowStartedAt());
        assertEquals(Instant.parse("2030-03-01T10:05:00.123Z"), open().getWindowExpiresAt());
    }

    @Test
    void isWindowOverdue_exactlyAtExpiryIsOverdueAndOneMillisecondBeforeIsNot() {
        PassengerRequestGroup g = open();

        assertFalse(g.isWindowOverdue(g.getWindowExpiresAt().minusMillis(1)));
        assertTrue(g.isWindowOverdue(g.getWindowExpiresAt()));
        assertTrue(g.isWindowOverdue(g.getWindowExpiresAt().plusMillis(1)));
    }

    @Test
    void isWindowOverdue_isFalseWhenTheGroupIsNotCollecting() {
        PassengerRequestGroup g = open();
        g.moveToAwaitingApproval(NOW);

        assertFalse(g.isWindowOverdue(g.getWindowExpiresAt().plus(1, ChronoUnit.HOURS)));
    }

    @Test
    void expire_marksTheGroupExpiredAndClearsTheOpenMarker() {
        PassengerRequestGroup g = open();

        g.expire();

        assertEquals(GroupStatus.EXPIRED, g.getStatus());
        assertNull(g.getOpenMarker());
        assertFalse(g.getStatus().isOpen());
    }

    @Test
    void expire_isRejectedUnlessTheGroupIsCollecting() {
        for (GroupStatus status : GroupStatus.values()) {
            if (status == GroupStatus.COLLECTING) continue;
            PassengerRequestGroup g = open();
            ReflectionTestUtils.setField(g, "status", status);

            ConflictException ex = assertThrows(ConflictException.class, g::expire);

            assertEquals("INVALID_TRANSITION", ex.code());
            assertEquals(status, g.getStatus());
        }
    }

    @Test
    void moveToAwaitingApproval_setsTheStatusAndTruncatedTimestampAndKeepsTheGroupOpen() {
        PassengerRequestGroup g = open();

        g.moveToAwaitingApproval(NOW.plusSeconds(30));

        assertEquals(GroupStatus.AWAITING_APPROVAL, g.getStatus());
        assertEquals(Instant.parse("2030-03-01T10:00:30.123Z"), g.getAwaitingSince());
        assertEquals(1, g.getOpenMarker());
        assertTrue(g.getStatus().isOpen());
    }

    @Test
    void moveToAwaitingApproval_isRejectedUnlessTheGroupIsCollecting() {
        for (GroupStatus status : GroupStatus.values()) {
            if (status == GroupStatus.COLLECTING) continue;
            PassengerRequestGroup g = open();
            ReflectionTestUtils.setField(g, "status", status);

            ConflictException ex = assertThrows(ConflictException.class, () -> g.moveToAwaitingApproval(NOW));

            assertEquals("INVALID_TRANSITION", ex.code());
            assertNull(g.getAwaitingSince());
        }
    }

    @Test
    void isOpen_isTrueOnlyForCollectingAwaitingApprovalAndApproved() {
        assertTrue(GroupStatus.COLLECTING.isOpen());
        assertTrue(GroupStatus.AWAITING_APPROVAL.isOpen());
        assertTrue(GroupStatus.APPROVED.isOpen());
        assertFalse(GroupStatus.NOT_APPROVED.isOpen());
        assertFalse(GroupStatus.EXPIRED.isOpen());
        assertFalse(GroupStatus.CLOSED.isOpen());
    }

    @Test
    void policy_holdsTheThresholdAndTheWindowInOnePlace() {
        assertEquals(3, PassengerRequestPolicy.REQUIRED_CONTRIBUTORS);
        assertEquals(5, PassengerRequestPolicy.WINDOW.toMinutes());
        assertEquals(500, PassengerRequestPolicy.MAX_MESSAGE_CODE_POINTS);
    }
}
