package com.dreamteam.safebus.trip.domain.repository;

import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.OccupancyEvent;
import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class OccupancyPersistenceTest {

    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final AtomicLong BUS_IDS = new AtomicLong(910_000_000L);

    @Autowired BusOccupancyRepository occupancyRepository;
    @Autowired OccupancyEventRepository eventRepository;

    private Long busId;

    @AfterEach
    void cleanUp() {
        eventRepository.deleteAll();
        occupancyRepository.findByBusId(busId).ifPresent(occupancyRepository::delete);
    }

    private BusOccupancy initializedRow() {
        busId = BUS_IDS.incrementAndGet();
        BusOccupancy o = BusOccupancy.create(1L, busId, 3L);
        o.initialize("init-" + busId, 7L, 0, NOW, NOW);
        return occupancyRepository.saveAndFlush(o);
    }

    @Test
    void theOccupancyRowRoundTrips() {
        BusOccupancy saved = initializedRow();

        BusOccupancy loaded = occupancyRepository.findByBusId(busId).orElseThrow();

        assertEquals(saved.getId(), loaded.getId());
        assertEquals(0, loaded.getCount());
        assertEquals(NOW, loaded.getLastCountAt());
    }

    @Test
    void onlyOneRowPerBusIsAllowed() {
        initializedRow();

        assertThrows(DataIntegrityViolationException.class,
            () -> occupancyRepository.saveAndFlush(BusOccupancy.create(1L, busId, 4L)));
    }

    @Test
    void aSequenceCanBeStoredOnlyOncePerEpoch() {
        BusOccupancy o = initializedRow();
        o.apply(7L, OccupancyEventType.ENTRY, 1, NOW);
        eventRepository.saveAndFlush(OccupancyEvent.applied("evt-a-" + busId, o, OccupancyEventType.ENTRY, 1, NOW, CLOCK));

        assertTrue(eventRepository.findByBusIdAndEpochAndSequence(busId, o.getEpoch(), 1).isPresent());
        assertThrows(DataIntegrityViolationException.class, () -> eventRepository.saveAndFlush(
            OccupancyEvent.applied("evt-b-" + busId, o, OccupancyEventType.ENTRY, 1, NOW, CLOCK)));
    }
}
