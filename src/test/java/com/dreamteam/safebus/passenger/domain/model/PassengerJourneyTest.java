package com.dreamteam.safebus.passenger.domain.model;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

class PassengerJourneyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void start_createsActiveJourneyWithActiveMarker1() {
        PassengerJourney j = PassengerJourney.start(1L, 2L, 3L, CLOCK);
        assertEquals(JourneyStatus.ACTIVE, j.getStatus());
        assertEquals(Integer.valueOf(1), j.getActiveMarker());
        assertNotNull(j.getStartedAt());
        assertNull(j.getEndedAt());
        assertNull(j.getEndReason());
    }

    @Test
    void end_activeJourney_setsEndedAndNullsMarker() {
        PassengerJourney j = PassengerJourney.start(1L, 2L, 3L, CLOCK);
        Instant endTime = Instant.parse("2026-01-01T13:00:00Z");
        boolean changed = j.end(JourneyEndReason.MANUAL, endTime);
        assertTrue(changed);
        assertEquals(JourneyStatus.ENDED, j.getStatus());
        assertEquals(JourneyEndReason.MANUAL, j.getEndReason());
        assertNotNull(j.getEndedAt());
        assertNull(j.getActiveMarker());
    }

    @Test
    void end_signOutReason_accepted() {
        PassengerJourney j = PassengerJourney.start(1L, 2L, 3L, CLOCK);
        j.end(JourneyEndReason.SIGN_OUT, Instant.now(CLOCK));
        assertEquals(JourneyEndReason.SIGN_OUT, j.getEndReason());
    }

    @Test
    void end_alreadyEnded_idempotentReturnsFalse() {
        PassengerJourney j = PassengerJourney.start(1L, 2L, 3L, CLOCK);
        Instant firstEnd = Instant.parse("2026-01-01T13:00:00Z");
        j.end(JourneyEndReason.MANUAL, firstEnd);
        Instant storedEndedAt = j.getEndedAt();

        boolean changed = j.end(JourneyEndReason.SIGN_OUT, Instant.parse("2026-01-01T14:00:00Z"));
        assertFalse(changed);
        assertEquals(JourneyEndReason.MANUAL, j.getEndReason());
        assertEquals(storedEndedAt, j.getEndedAt());
        assertNull(j.getActiveMarker());
    }
}

// DB-backed constraint test in a nested Spring context
@SpringBootTest
@ActiveProfiles("test")
class PassengerJourneyConstraintTest {

    @Autowired
    com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository journeyRepository;
    @Autowired Clock clock;

    @Test
    @Transactional
    void twoActiveJourneysForSamePassenger_violatesUniqueConstraint() {
        PassengerJourney first  = journeyRepository.saveAndFlush(PassengerJourney.start(5000L, 1L, 1L, clock));
        assertThrows(DataIntegrityViolationException.class, () -> {
            journeyRepository.saveAndFlush(PassengerJourney.start(5000L, 2L, 2L, clock));
        });
    }
}
