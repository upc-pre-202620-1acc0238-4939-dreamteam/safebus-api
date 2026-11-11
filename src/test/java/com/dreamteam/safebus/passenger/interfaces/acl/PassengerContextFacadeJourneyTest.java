package com.dreamteam.safebus.passenger.interfaces.acl;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PassengerContextFacadeJourneyTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123Z"), ZoneOffset.UTC);
    private static final Long USER_ID = 84001L;

    @Autowired PassengerContextFacade facade;
    @Autowired PassengerJourneyRepository journeyRepository;

    private final List<Long> journeyIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        journeyRepository.deleteAllById(journeyIds);
    }

    private PassengerJourney save(Long userId, boolean ended) {
        PassengerJourney journey = PassengerJourney.start(userId, 85001L, 86001L, CLOCK);
        if (ended) {
            journey.end(JourneyEndReason.MANUAL, CLOCK.instant().plusSeconds(60));
        }
        PassengerJourney saved = journeyRepository.saveAndFlush(journey);
        journeyIds.add(saved.getId());
        return saved;
    }

    @Test
    void findJourneyOfPassenger_ownActiveJourneyIsReturned() {
        PassengerJourney journey = save(USER_ID, false);

        var view = facade.findJourneyOfPassenger(USER_ID, journey.getId()).orElseThrow();

        assertEquals(new PassengerContextFacade.JourneyView(journey.getId(), 85001L, 86001L, "ACTIVE",
            Instant.parse("2030-01-01T10:00:00.123Z"), null), view);
    }

    @Test
    void findJourneyOfPassenger_ownEndedJourneyIsReturnedWithItsEnd() {
        PassengerJourney journey = save(USER_ID, true);

        var view = facade.findJourneyOfPassenger(USER_ID, journey.getId()).orElseThrow();

        assertEquals("ENDED", view.status());
        assertEquals(Instant.parse("2030-01-01T10:01:00.123Z"), view.endedAt());
    }

    @Test
    void findJourneyOfPassenger_anotherAccountsJourneyIsEmpty() {
        PassengerJourney other = save(USER_ID + 1, false);

        assertTrue(facade.findJourneyOfPassenger(USER_ID, other.getId()).isEmpty());
    }

    @Test
    void findJourneyOfPassenger_unknownIdIsEmpty() {
        assertTrue(facade.findJourneyOfPassenger(USER_ID, 999_999_999L).isEmpty());
        assertNull(journeyRepository.findById(999_999_999L).orElse(null));
    }
}
