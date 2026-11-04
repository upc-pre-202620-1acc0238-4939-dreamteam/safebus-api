package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.exceptions.SequenceGapException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.dreamteam.safebus.trip.domain.model.OccupancyEventType.ENTRY;
import static com.dreamteam.safebus.trip.domain.model.OccupancyEventType.EXIT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BusOccupancyTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Long SHIFT = 7L;

    private BusOccupancy initialized() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);
        o.initialize("init-1", SHIFT, 0, NOW, NOW);
        return o;
    }

    @Test
    void startsNotInitialized() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);

        assertEquals(ConsistencyState.NOT_INITIALIZED, o.getConsistency());
        assertNull(o.getCount());
    }

    @Test
    void initializationSetsCountToZeroAndStartsANewEpoch() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);

        InitializationOutcome outcome = o.initialize("init-1", SHIFT, 0, NOW, NOW);

        assertEquals(InitializationOutcome.APPLIED, outcome);
        assertEquals(0, o.getCount());
        assertEquals(ConsistencyState.CONSISTENT, o.getConsistency());
        assertEquals(0L, o.getLastSequence());
        assertEquals(1, o.getEpoch());
        assertEquals(NOW, o.getLastCountAt());
    }

    @Test
    void initializationRejectsANonZeroCount() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> o.initialize("init-1", SHIFT, 5, NOW, NOW));

        assertEquals("INVALID_INITIAL_COUNT", ex.code());
    }

    @Test
    void initializationRejectsAConfirmationTimeTooFarInTheFuture() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> o.initialize("init-1", SHIFT, 0, NOW.plusSeconds(301), NOW));

        assertEquals("INVALID_CAPTURE_TIME", ex.code());
    }

    @Test
    void identicalInitializationRetryIsADuplicateAndChangesNothing() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 1, NOW);

        InitializationOutcome outcome = o.initialize("init-1", SHIFT, 0, NOW, NOW);

        assertEquals(InitializationOutcome.DUPLICATE, outcome);
        assertEquals(1, o.getCount());
        assertEquals(1L, o.getLastSequence());
    }

    @Test
    void reinitializingAConsistentCountOnTheSameShiftConflicts() {
        BusOccupancy o = initialized();

        ConflictException ex = assertThrows(ConflictException.class,
            () -> o.initialize("init-2", SHIFT, 0, NOW, NOW));

        assertEquals("ALREADY_INITIALIZED", ex.code());
    }

    @Test
    void reinitializingIsAllowedForAnotherShift() {
        BusOccupancy o = initialized();
        int epochBefore = o.getEpoch();

        o.initialize("init-2", 8L, 0, NOW, NOW);

        assertEquals(8L, o.getShiftId());
        assertEquals(epochBefore + 1, o.getEpoch());
        assertEquals(0L, o.getLastSequence());
    }

    @Test
    void reinitializingIsAllowedAfterAnInconsistency() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, EXIT, 1, NOW);

        o.initialize("init-2", SHIFT, 0, NOW, NOW);

        assertEquals(ConsistencyState.CONSISTENT, o.getConsistency());
        assertEquals(0, o.getCount());
    }

    @Test
    void entriesAndExitsChangeTheCountInSequence() {
        BusOccupancy o = initialized();

        assertEquals(EventOutcome.APPLIED, o.apply(SHIFT, ENTRY, 1, NOW));
        assertEquals(EventOutcome.APPLIED, o.apply(SHIFT, ENTRY, 2, NOW));
        assertEquals(EventOutcome.APPLIED, o.apply(SHIFT, EXIT, 3, NOW.plusSeconds(5)));

        assertEquals(1, o.getCount());
        assertEquals(3L, o.getLastSequence());
        assertEquals(NOW.plusSeconds(5), o.getLastCountAt());
    }

    @Test
    void eventsBeforeInitializationAreRejected() {
        BusOccupancy o = BusOccupancy.create(1L, 2L, 3L);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> o.apply(SHIFT, ENTRY, 1, NOW));

        assertEquals("OCCUPANCY_NOT_INITIALIZED", ex.code());
    }

    @Test
    void eventsForAnotherShiftAreRejectedAsNotInitialized() {
        BusOccupancy o = initialized();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> o.apply(99L, ENTRY, 1, NOW));

        assertEquals("OCCUPANCY_NOT_INITIALIZED", ex.code());
    }

    @Test
    void anAlreadyAppliedSequenceConflicts() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 1, NOW);

        ConflictException ex = assertThrows(ConflictException.class,
            () -> o.apply(SHIFT, ENTRY, 1, NOW));

        assertEquals("SEQUENCE_ALREADY_APPLIED", ex.code());
    }

    @Test
    void aSkippedSequenceMarksAGapAndKeepsTheCount() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 1, NOW);

        EventOutcome outcome = o.apply(SHIFT, ENTRY, 3, NOW);

        assertEquals(EventOutcome.GAP, outcome);
        assertEquals(ConsistencyState.GAP, o.getConsistency());
        assertEquals(3L, o.getRecoverAtSequence());
        assertEquals(1, o.getCount());
        SequenceGapException gap = o.gap();
        assertEquals(2L, gap.expectedSequence());
        assertEquals("SEQUENCE_GAP", gap.code());
    }

    @Test
    void inAGapTheNextExpectedSequenceStillApplies() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 1, NOW);
        o.apply(SHIFT, ENTRY, 4, NOW);

        EventOutcome outcome = o.apply(SHIFT, ENTRY, 2, NOW);

        assertEquals(EventOutcome.APPLIED, outcome);
        assertEquals(2, o.getCount());
        assertEquals(2L, o.getLastSequence());
        assertEquals(ConsistencyState.GAP, o.getConsistency());
    }

    @Test
    void aFurtherAheadSequenceRaisesTheRecoveryPointToTheHighestSeen() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 3, NOW);
        o.apply(SHIFT, ENTRY, 6, NOW);
        o.apply(SHIFT, ENTRY, 5, NOW);

        assertEquals(6L, o.getRecoverAtSequence());
    }

    @Test
    void consistencyReturnsExactlyWhenTheRecoveryPointIsApplied() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 3, NOW);

        o.apply(SHIFT, ENTRY, 1, NOW);
        assertEquals(ConsistencyState.GAP, o.getConsistency());
        o.apply(SHIFT, ENTRY, 2, NOW);
        assertEquals(ConsistencyState.GAP, o.getConsistency());
        o.apply(SHIFT, ENTRY, 3, NOW);

        assertEquals(ConsistencyState.CONSISTENT, o.getConsistency());
        assertNull(o.getRecoverAtSequence());
        assertEquals(3, o.getCount());
    }

    @Test
    void anExitAtZeroMarksTheCountInconsistentWithoutAdvancing() {
        BusOccupancy o = initialized();

        EventOutcome outcome = o.apply(SHIFT, EXIT, 1, NOW);

        assertEquals(EventOutcome.NEGATIVE, outcome);
        assertEquals(ConsistencyState.INCONSISTENT, o.getConsistency());
        assertEquals(0, o.getCount());
        assertEquals(0L, o.getLastSequence());
    }

    @Test
    void anInconsistentCountRejectsFurtherEvents() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, EXIT, 1, NOW);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> o.apply(SHIFT, ENTRY, 1, NOW));

        assertEquals("OCCUPANCY_INCONSISTENT", ex.code());
    }

    @Test
    void anExitAtZeroWhileRecoveringFromAGapMarksInconsistent() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 3, NOW);

        EventOutcome outcome = o.apply(SHIFT, EXIT, 1, NOW);

        assertEquals(EventOutcome.NEGATIVE, outcome);
        assertEquals(ConsistencyState.INCONSISTENT, o.getConsistency());
        assertNull(o.getRecoverAtSequence());
    }

    @Test
    void resetForANewSourceClearsEverythingAndBumpsTheEpoch() {
        BusOccupancy o = initialized();
        o.apply(SHIFT, ENTRY, 1, NOW);
        o.recordHeartbeat(NOW);
        int epochBefore = o.getEpoch();

        o.resetForNewSource(9L);

        assertEquals(ConsistencyState.NOT_INITIALIZED, o.getConsistency());
        assertNull(o.getCount());
        assertNull(o.getShiftId());
        assertNull(o.getLastHeartbeatAt());
        assertEquals(0L, o.getLastSequence());
        assertEquals(9L, o.getSourceId());
        assertEquals(epochBefore + 1, o.getEpoch());
    }

    @Test
    void onlyAHeartbeatUpdatesTheHeartbeatTime() {
        BusOccupancy o = initialized();
        assertNull(o.getLastHeartbeatAt());

        o.apply(SHIFT, ENTRY, 1, NOW);
        assertNull(o.getLastHeartbeatAt());

        o.recordHeartbeat(NOW.plusSeconds(30));
        assertEquals(NOW.plusSeconds(30), o.getLastHeartbeatAt());
    }
}
