package com.dreamteam.safebus.passenger.infrastructure;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.trip.application.ShiftJourneyClosurePort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class ShiftJourneyClosureAdapterTest {

    private static final Instant START = Instant.parse("2030-04-01T08:00:00Z");
    private static final Instant CLOSE = Instant.parse("2030-04-01T16:00:00.123456Z");
    private static final Clock FIXED = Clock.fixed(START, ZoneOffset.UTC);

    @Autowired ShiftJourneyClosurePort port;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired TransactionTemplate tx;

    @AfterEach
    void cleanUp() {
        journeyRepository.deleteAll();
    }

    private int endInTransaction(Long shiftId) {
        return tx.execute(s -> port.endJourneysOfShift(shiftId, CLOSE));
    }

    @Test
    void endsOnlyActiveJourneysOfThatShiftWithShiftClosed() {
        PassengerJourney a = journeyRepository.saveAndFlush(PassengerJourney.start(8001L, 50L, 10L, FIXED));
        PassengerJourney b = journeyRepository.saveAndFlush(PassengerJourney.start(8002L, 50L, 10L, FIXED));

        int count = endInTransaction(10L);

        assertEquals(2, count);
        for (Long id : new Long[] {a.getId(), b.getId()}) {
            PassengerJourney loaded = journeyRepository.findById(id).orElseThrow();
            assertEquals(JourneyStatus.ENDED, loaded.getStatus());
            assertEquals(JourneyEndReason.SHIFT_CLOSED, loaded.getEndReason());
            assertEquals(Instant.parse("2030-04-01T16:00:00.123Z"), loaded.getEndedAt());
            assertNull(loaded.getActiveMarker());
        }
    }

    @Test
    void leavesJourneysOfAnotherShiftUntouched() {
        PassengerJourney other = journeyRepository.saveAndFlush(PassengerJourney.start(8003L, 51L, 11L, FIXED));
        journeyRepository.saveAndFlush(PassengerJourney.start(8004L, 50L, 10L, FIXED));

        int count = endInTransaction(10L);

        assertEquals(1, count);
        PassengerJourney untouched = journeyRepository.findById(other.getId()).orElseThrow();
        assertEquals(JourneyStatus.ACTIVE, untouched.getStatus());
        assertNull(untouched.getEndReason());
        assertNull(untouched.getEndedAt());
        assertEquals(1, untouched.getActiveMarker());
    }

    @Test
    void alreadyEndedJourneyKeepsItsOriginalReasonAndTime() {
        Instant manualEnd = Instant.parse("2030-04-01T12:00:00Z");
        PassengerJourney ended = PassengerJourney.start(8005L, 50L, 10L, FIXED);
        ended.end(JourneyEndReason.MANUAL, manualEnd);
        ended = journeyRepository.saveAndFlush(ended);
        journeyRepository.saveAndFlush(PassengerJourney.start(8006L, 50L, 10L, FIXED));

        int count = endInTransaction(10L);

        assertEquals(1, count);
        PassengerJourney loaded = journeyRepository.findById(ended.getId()).orElseThrow();
        assertEquals(JourneyEndReason.MANUAL, loaded.getEndReason());
        assertEquals(manualEnd, loaded.getEndedAt());
    }

    @Test
    void shiftWithoutJourneys_returnsZero() {
        assertEquals(0, endInTransaction(12L));
    }

    @Test
    void withoutTransaction_throwsIllegalTransactionState() {
        assertThrows(IllegalTransactionStateException.class,
            () -> port.endJourneysOfShift(10L, CLOSE));
    }
}
