package com.dreamteam.safebus.passenger.infrastructure;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.trip.application.PassengerJourneyAccessPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class PassengerJourneyAccessAdapterTest {

    @Autowired ApplicationContext context;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired Clock clock;

    @AfterEach
    void cleanUp() {
        journeyRepository.deleteAll();
    }

    @Test
    void exactlyOnePassengerJourneyAccessPortBean() {
        Map<String, PassengerJourneyAccessPort> beans = context.getBeansOfType(PassengerJourneyAccessPort.class);
        assertEquals(1, beans.size(), "expected exactly one PassengerJourneyAccessPort bean");
        assertTrue(beans.values().iterator().next() instanceof PassengerJourneyAccessAdapter,
            "the bean must be the real adapter from the passenger context");
    }

    @Test
    void hasActiveJourney_activeJourneyOnSameBus_returnsTrue() {
        journeyRepository.saveAndFlush(PassengerJourney.start(1001L, 50L, 10L, clock));

        PassengerJourneyAccessPort adapter = context.getBean(PassengerJourneyAccessPort.class);
        assertTrue(adapter.hasActiveJourneyOnBus(1001L, 50L));
    }

    @Test
    void hasActiveJourney_endedJourney_returnsFalse() {
        PassengerJourney journey = PassengerJourney.start(1002L, 50L, 10L, clock);
        journey.end(com.dreamteam.safebus.passenger.domain.model.JourneyEndReason.MANUAL, Instant.now(clock));
        journeyRepository.saveAndFlush(journey);

        PassengerJourneyAccessPort adapter = context.getBean(PassengerJourneyAccessPort.class);
        assertFalse(adapter.hasActiveJourneyOnBus(1002L, 50L));
    }

    @Test
    void hasActiveJourney_journeyOnDifferentBus_returnsFalse() {
        journeyRepository.saveAndFlush(PassengerJourney.start(1003L, 99L, 10L, clock));

        PassengerJourneyAccessPort adapter = context.getBean(PassengerJourneyAccessPort.class);
        assertFalse(adapter.hasActiveJourneyOnBus(1003L, 50L));
    }
}
