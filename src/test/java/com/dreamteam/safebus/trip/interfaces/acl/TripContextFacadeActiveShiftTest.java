package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.time.Clock;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class TripContextFacadeActiveShiftTest {

    @Autowired TripContextFacade facade;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired Clock clock;

    @AfterEach
    void tearDown() {
        driverShiftRepository.deleteAll();
    }

    @Test
    void findActiveShiftByBusId_noShift_returnsEmpty() {
        assertTrue(facade.findActiveShiftByBusId(999L).isEmpty());
    }

    @Test
    void findActiveShiftByBusId_oneActiveShift_returnsIt() {
        DriverShift s = driverShiftRepository.save(DriverShift.start(1L, 2L, 100L, 3L, clock));
        Optional<TripContextFacade.ShiftInfo> result = facade.findActiveShiftByBusId(100L);
        assertTrue(result.isPresent());
        assertEquals(s.getId(), result.get().shiftId());
        assertEquals(100L, result.get().busId());
    }

    @Test
    void findActiveShiftByBusId_severalActiveShifts_returnsLatest() throws Exception {
        DriverShift old  = driverShiftRepository.save(DriverShift.start(1L, 2L, 200L, 3L, clock));
        Thread.sleep(5);
        DriverShift newer = driverShiftRepository.save(DriverShift.start(2L, 2L, 200L, 3L, clock));

        Optional<TripContextFacade.ShiftInfo> result = facade.findActiveShiftByBusId(200L);
        assertTrue(result.isPresent());
        assertEquals(newer.getId(), result.get().shiftId());
    }

    @Test
    void findActiveShiftByBusId_onlyClosedShift_returnsEmpty() throws Exception {
        DriverShift s = driverShiftRepository.save(DriverShift.start(1L, 2L, 300L, 3L, clock));
        Field f = DriverShift.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(s, ShiftStatus.CLOSED);
        driverShiftRepository.save(s);

        assertTrue(facade.findActiveShiftByBusId(300L).isEmpty());
    }
}
