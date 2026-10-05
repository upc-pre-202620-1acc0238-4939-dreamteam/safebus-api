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
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PassengerContextFacadeTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123Z"), ZoneOffset.UTC);
    private static final Long USER_ID = 81001L;

    @Autowired PassengerContextFacade facade;
    @Autowired PassengerJourneyRepository journeyRepository;

    private final List<Long> journeyIds = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        journeyRepository.deleteAllById(journeyIds);
    }

    private PassengerJourney save(Long userId, Long busId, Long shiftId, boolean ended) {
        PassengerJourney journey = PassengerJourney.start(userId, busId, shiftId, CLOCK);
        if (ended) {
            journey.end(JourneyEndReason.MANUAL, CLOCK.instant().plusSeconds(60));
        }
        PassengerJourney saved = journeyRepository.saveAndFlush(journey);
        journeyIds.add(saved.getId());
        return saved;
    }

    @Test
    void findActiveJourney_activeJourneyReturnsExactlyItsThreeIds() {
        PassengerJourney journey = save(USER_ID, 82001L, 83001L, false);

        var result = facade.findActiveJourney(USER_ID).orElseThrow();

        assertEquals(new PassengerContextFacade.ActiveJourneyInfo(journey.getId(), 82001L, 83001L), result);
        assertEquals(Set.of("journeyId", "busId", "shiftId"), Arrays.stream(result.getClass().getRecordComponents())
            .map(component -> component.getName()).collect(Collectors.toSet()));
        assertUnchanged(journey);
    }

    @Test
    void findActiveJourney_noJourneyReturnsEmpty() {
        long count = journeyRepository.count();
        assertTrue(facade.findActiveJourney(USER_ID).isEmpty());
        assertEquals(count, journeyRepository.count());
    }

    @Test
    void findActiveJourney_endedJourneyReturnsEmpty() {
        PassengerJourney ended = save(USER_ID, 82001L, 83001L, true);

        assertTrue(facade.findActiveJourney(USER_ID).isEmpty());

        assertEquals(1, journeyRepository.count());
        assertUnchanged(ended);
    }

    @Test
    void findActiveJourney_anotherPassengersJourneyIsNeverReturned() {
        PassengerJourney other = save(USER_ID + 1, 82001L, 83001L, false);

        assertTrue(facade.findActiveJourney(USER_ID).isEmpty());

        assertEquals(1, journeyRepository.count());
        assertUnchanged(other);
    }

    @Test
    void findActiveJourney_selectsOwnActiveJourneyAmongEndedAndOtherPassengerJourneys() {
        PassengerJourney ended = save(USER_ID, 82002L, 83002L, true);
        PassengerJourney other = save(USER_ID + 1, 82003L, 83003L, false);
        PassengerJourney active = save(USER_ID, 82001L, 83001L, false);

        var result = facade.findActiveJourney(USER_ID).orElseThrow();

        assertEquals(new PassengerContextFacade.ActiveJourneyInfo(active.getId(), active.getBusId(), active.getShiftId()), result);
        assertEquals(3, journeyRepository.count());
        assertUnchanged(ended);
        assertUnchanged(other);
        assertUnchanged(active);
    }

    private void assertUnchanged(PassengerJourney before) {
        PassengerJourney after = journeyRepository.findById(before.getId()).orElseThrow();
        assertEquals(before.getUserAccountId(), after.getUserAccountId());
        assertEquals(before.getBusId(), after.getBusId());
        assertEquals(before.getShiftId(), after.getShiftId());
        assertEquals(before.getStatus(), after.getStatus());
        assertEquals(before.getStartedAt(), after.getStartedAt());
        assertEquals(before.getEndedAt(), after.getEndedAt());
        assertEquals(before.getEndReason(), after.getEndReason());
        assertEquals(before.getActiveMarker(), after.getActiveMarker());
    }
}
