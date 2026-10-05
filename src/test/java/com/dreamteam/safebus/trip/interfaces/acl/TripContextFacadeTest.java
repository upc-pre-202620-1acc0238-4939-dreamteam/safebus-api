package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TripContextFacadeTest {

    @Autowired TripContextFacade facade;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired Clock clock;

    @AfterEach
    void tearDown() {
        driverShiftRepository.deleteAll();
    }

    @Test
    void findShiftById_found_returnsShiftInfo() {
        DriverShift shift = driverShiftRepository.save(
            DriverShift.start(100L, 200L, 300L, 400L, clock));

        Optional<TripContextFacade.ShiftInfo> result = facade.findShiftById(shift.getId());

        assertTrue(result.isPresent());
        TripContextFacade.ShiftInfo info = result.get();
        assertEquals(shift.getId(), info.shiftId());
        assertEquals(200L, info.driverId());
        assertEquals(300L, info.busId());
        assertEquals(400L, info.routeId());
        assertEquals(ShiftStatus.ACTIVE.name(), info.status());
    }

    @Test
    void findShiftById_notFound_returnsEmpty() {
        assertTrue(facade.findShiftById(999999L).isEmpty());
    }
}
