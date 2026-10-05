package com.dreamteam.safebus.trip.domain.model;

import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
@ActiveProfiles("test")
class DriverShiftPersistenceTest {

    @Autowired DriverShiftRepository repository;
    @Autowired TransactionTemplate tx;
    @Autowired Clock clock;

    @Test
    void closedShift_roundTripsLastKnownPosition() {
        DriverShift shift = DriverShift.start(9100L, 1L, 2L, 3L, clock);
        Instant capturedAt = Instant.parse("2030-03-01T10:00:00.250Z");
        shift.close(Instant.parse("2030-03-01T16:00:00.500Z"), new GeoPoint(-12.046, -77.042), capturedAt);
        Long id = repository.saveAndFlush(shift).getId();
        try {
            DriverShift loaded = tx.execute(s -> repository.findById(id).orElseThrow());
            assertEquals(ShiftStatus.CLOSED, loaded.getStatus());
            assertEquals(Instant.parse("2030-03-01T16:00:00.500Z"), loaded.getClosedAt());
            assertEquals(-12.046, loaded.getLastKnownPoint().getLatitude());
            assertEquals(-77.042, loaded.getLastKnownPoint().getLongitude());
            assertEquals(capturedAt, loaded.getLastKnownCapturedAt());
        } finally {
            repository.deleteById(id);
        }
    }

    @Test
    void closedShiftWithoutPosition_roundTripsNulls() {
        DriverShift shift = DriverShift.start(9101L, 1L, 2L, 3L, clock);
        shift.close(Instant.parse("2030-03-01T16:00:00Z"), null, null);
        Long id = repository.saveAndFlush(shift).getId();
        try {
            DriverShift loaded = tx.execute(s -> repository.findById(id).orElseThrow());
            assertEquals(ShiftStatus.CLOSED, loaded.getStatus());
            assertNull(loaded.getLastKnownPoint());
            assertNull(loaded.getLastKnownCapturedAt());
        } finally {
            repository.deleteById(id);
        }
    }
}
