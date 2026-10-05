package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class TripContextFacadeBatchTest {

    private static final Instant T0 = Instant.parse("2030-04-01T08:00:00Z");
    private static final long BUS_A = 7101L;
    private static final long BUS_B = 7102L;
    private static final long BUS_C = 7103L;
    private static final long DRIVER = 7201L;
    private static final long ROUTE = 7301L;

    @Autowired TripContextFacade facade;
    @MockitoSpyBean DriverShiftRepository driverShiftRepository;
    @MockitoSpyBean VehicleLocationRepository vehicleLocationRepository;

    private final List<DriverShift> shifts = new ArrayList<>();
    private final List<VehicleLocation> locations = new ArrayList<>();
    private long nextAssignment = 7400L;

    @AfterEach
    void cleanUp() {
        driverShiftRepository.deleteAll(shifts);
        vehicleLocationRepository.deleteAll(locations);
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private DriverShift activeShift(long busId, Instant startedAt) {
        DriverShift s = driverShiftRepository.save(
            DriverShift.start(nextAssignment++, DRIVER, busId, ROUTE, at(startedAt)));
        shifts.add(s);
        return s;
    }

    private DriverShift closedShift(long busId, Instant startedAt) {
        DriverShift s = activeShift(busId, startedAt);
        s.close(startedAt.plusSeconds(60), null, null);
        return driverShiftRepository.save(s);
    }

    private VehicleLocation positionOf(long busId, double lat, double lon, double accuracy, Instant capturedAt) {
        VehicleLocation vl = VehicleLocation.empty(busId);
        vl.applyIfNewer(LocationEvent.create("batch-" + busId + "-" + capturedAt, 1L, busId,
            lat, lon, accuracy, capturedAt, at(capturedAt)), at(capturedAt));
        VehicleLocation saved = vehicleLocationRepository.save(vl);
        locations.add(saved);
        return saved;
    }

    // --- findActiveShiftsByBusIds ---

    @Test
    void findActiveShiftsByBusIds_noShifts_returnsEmptyMap() {
        assertTrue(facade.findActiveShiftsByBusIds(List.of(BUS_A, BUS_B)).isEmpty());
    }

    @Test
    void findActiveShiftsByBusIds_oneShift_returnsItsFields() {
        DriverShift s = activeShift(BUS_A, T0);

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(List.of(BUS_A));

        assertEquals(Map.of(BUS_A, new TripContextFacade.ActiveShiftView(
            s.getId(), BUS_A, DRIVER, ROUTE, T0)), result);
    }

    @Test
    void findActiveShiftsByBusIds_severalBuses_returnsOneEntryPerRequestedBus() {
        DriverShift a = activeShift(BUS_A, T0);
        DriverShift b = activeShift(BUS_B, T0.plusSeconds(10));
        activeShift(BUS_C, T0);

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(Set.of(BUS_A, BUS_B));

        assertEquals(Set.of(BUS_A, BUS_B), result.keySet());
        assertEquals(a.getId(), result.get(BUS_A).shiftId());
        assertEquals(b.getId(), result.get(BUS_B).shiftId());
    }

    @Test
    void findActiveShiftsByBusIds_twoActiveShiftsOnTheSameBus_returnsTheOneWithTheGreatestStartedAt() {
        activeShift(BUS_A, T0);
        DriverShift newer = activeShift(BUS_A, T0.plusSeconds(30));
        activeShift(BUS_A, T0.plusSeconds(10));

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(List.of(BUS_A));

        assertEquals(newer.getId(), result.get(BUS_A).shiftId());
        assertEquals(T0.plusSeconds(30), result.get(BUS_A).startedAt());
    }

    @Test
    void findActiveShiftsByBusIds_identicalStartedAt_greatestShiftIdWins() {
        DriverShift first = activeShift(BUS_A, T0);
        DriverShift second = activeShift(BUS_A, T0);
        assertTrue(second.getId() > first.getId());

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(List.of(BUS_A));

        assertEquals(second.getId(), result.get(BUS_A).shiftId());
    }

    @Test
    void findActiveShiftsByBusIds_closedShiftIsIgnored() {
        closedShift(BUS_A, T0);

        assertTrue(facade.findActiveShiftsByBusIds(List.of(BUS_A)).isEmpty());
    }

    @Test
    void findActiveShiftsByBusIds_closedShiftNewerThanTheActiveOneDoesNotHideIt() {
        DriverShift active = activeShift(BUS_A, T0);
        closedShift(BUS_A, T0.plusSeconds(120));

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(List.of(BUS_A));

        assertEquals(active.getId(), result.get(BUS_A).shiftId());
    }

    @Test
    void findActiveShiftsByBusIds_emptyOrNullCollectionReturnsEmptyMapWithoutQuerying() {
        assertTrue(facade.findActiveShiftsByBusIds(List.of()).isEmpty());
        assertTrue(facade.findActiveShiftsByBusIds(null).isEmpty());

        verify(driverShiftRepository, never()).findByBusIdInAndStatus(any(), any());
    }

    @Test
    void findActiveShiftsByBusIds_resultIsImmutable() {
        activeShift(BUS_A, T0);

        Map<Long, TripContextFacade.ActiveShiftView> result = facade.findActiveShiftsByBusIds(List.of(BUS_A));

        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    // --- findLastPositionsByBusIds ---

    @Test
    void findLastPositionsByBusIds_noRows_returnsEmptyMap() {
        assertTrue(facade.findLastPositionsByBusIds(List.of(BUS_A, BUS_B)).isEmpty());
    }

    @Test
    void findLastPositionsByBusIds_oneRow_returnsItsFields() {
        Instant captured = T0.plusSeconds(5);
        positionOf(BUS_A, -12.05, -77.04, 8.5, captured);

        Map<Long, TripContextFacade.BusPositionView> result = facade.findLastPositionsByBusIds(List.of(BUS_A));

        assertEquals(Map.of(BUS_A, new TripContextFacade.BusPositionView(
            BUS_A, -12.05, -77.04, 8.5, captured)), result);
    }

    @Test
    void findLastPositionsByBusIds_severalRows_returnsOnlyTheRequestedBuses() {
        positionOf(BUS_A, -12.0, -77.0, 5.0, T0);
        positionOf(BUS_B, -13.0, -76.0, 6.0, T0.plusSeconds(1));
        positionOf(BUS_C, -14.0, -75.0, 7.0, T0.plusSeconds(2));

        Map<Long, TripContextFacade.BusPositionView> result =
            facade.findLastPositionsByBusIds(List.of(BUS_A, BUS_B));

        assertEquals(Set.of(BUS_A, BUS_B), result.keySet());
        assertEquals(-13.0, result.get(BUS_B).latitude());
        assertEquals(-76.0, result.get(BUS_B).longitude());
        assertEquals(6.0, result.get(BUS_B).accuracyMeters());
        assertEquals(T0.plusSeconds(1), result.get(BUS_B).capturedAt());
    }

    @Test
    void findLastPositionsByBusIds_rowWithoutPointIsTreatedAsAbsent() {
        VehicleLocation empty = vehicleLocationRepository.save(VehicleLocation.empty(BUS_A));
        locations.add(empty);
        assertNull(vehicleLocationRepository.findByBusId(BUS_A).orElseThrow().getPoint());
        positionOf(BUS_B, -13.0, -76.0, 6.0, T0);

        Map<Long, TripContextFacade.BusPositionView> result =
            facade.findLastPositionsByBusIds(List.of(BUS_A, BUS_B));

        assertEquals(Set.of(BUS_B), result.keySet());
    }

    @Test
    void findLastPositionsByBusIds_emptyOrNullCollectionReturnsEmptyMapWithoutQuerying() {
        assertTrue(facade.findLastPositionsByBusIds(List.of()).isEmpty());
        assertTrue(facade.findLastPositionsByBusIds(null).isEmpty());

        verify(vehicleLocationRepository, never()).findByBusIdIn(any());
    }

    @Test
    void findLastPositionsByBusIds_resultIsImmutable() {
        positionOf(BUS_A, -12.0, -77.0, 5.0, T0);

        Map<Long, TripContextFacade.BusPositionView> result = facade.findLastPositionsByBusIds(List.of(BUS_A));

        assertThrows(UnsupportedOperationException.class, result::clear);
    }
}
