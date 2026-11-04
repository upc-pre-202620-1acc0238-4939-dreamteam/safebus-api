package com.dreamteam.safebus.fleetmonitoring.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.BusOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.EmergencyOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.OccupancyOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.PassengerGroupOverview;
import com.dreamteam.safebus.fleetmonitoring.domain.LocationStatus;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.interfaces.acl.SafetycaseContextFacade;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GetFleetOverviewQueryServiceTest {

    private static final Instant NOW = Instant.parse("2030-08-01T12:00:00Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired GetFleetOverviewQueryService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired BusOccupancyRepository occupancyRepository;
    @Autowired PassengerRequestGroupRepository groupRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoSpyBean FleetContextFacade fleetFacade;
    @MockitoSpyBean TripContextFacade tripFacade;
    @MockitoSpyBean SafetycaseContextFacade safetycaseFacade;

    private Company company;
    private Company otherCompany;
    private Route route;
    private Driver driver;
    private Bus busA;
    private Bus busB;
    private DriverShift shiftA;
    private long nextAssignment = 8000L;
    private final List<String> emergencyIds = new ArrayList<>();
    private final List<DriverShift> shifts = new ArrayList<>();
    private final List<VehicleLocation> locations = new ArrayList<>();
    private final List<Bus> buses = new ArrayList<>();
    private final List<BusOccupancy> occupancies = new ArrayList<>();
    private final List<PassengerRequestGroup> groups = new ArrayList<>();

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Overview Company"));
        otherCompany = companyRepository.save(Company.create("Other Overview Company"));
        route = routeRepository.save(Route.create(company.getId(), "Overview Route", "Origin", "Destination"));
        driver = driverRepository.save(Driver.create(company.getId(), 8101L, "Overview Driver",
            () -> "OV-DRV-QR-1", Duration.ofDays(365), clock));
        // saved out of plate order on purpose
        busB = saveBus(company, "OV-B");
        busA = saveBus(company, "OV-A");
        shiftA = activeShift(busA, NOW.minus(Duration.ofHours(1)));
        savePosition(busA, -12.05, -77.04, 8.0, NOW.minusSeconds(30));
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(1L, "SUPERVISOR", company.getId()));
    }

    @AfterEach
    void cleanUp() {
        emergencyRepository.deleteAllById(emergencyIds);
        groupRepository.deleteAllById(groups.stream().map(PassengerRequestGroup::getId).toList());
        occupancyRepository.deleteAllById(occupancies.stream().map(BusOccupancy::getId).toList());
        driverShiftRepository.deleteAll(shifts);
        vehicleLocationRepository.deleteAll(locations);
        busRepository.deleteAll(buses);
        driverRepository.delete(driver);
        routeRepository.delete(route);
        companyRepository.deleteAll(List.of(company, otherCompany));
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private Bus saveBus(Company owner, String plate) {
        Bus b = busRepository.save(Bus.create(owner.getId(), plate, () -> "ov-qr-" + plate));
        buses.add(b);
        return b;
    }

    private DriverShift activeShift(Bus bus, Instant startedAt) {
        DriverShift s = driverShiftRepository.save(
            DriverShift.start(nextAssignment++, driver.getId(), bus.getId(), route.getId(), at(startedAt)));
        shifts.add(s);
        return s;
    }

    private void savePosition(Bus bus, double lat, double lon, double accuracy, Instant capturedAt) {
        VehicleLocation vl = VehicleLocation.empty(bus.getId());
        vl.applyIfNewer(LocationEvent.create("ov-" + bus.getId() + "-" + capturedAt, 1L, bus.getId(),
            lat, lon, accuracy, capturedAt, at(capturedAt)), at(capturedAt));
        locations.add(vehicleLocationRepository.save(vl));
    }

    private Emergency emergency(String id, Company owner, Bus bus, Long shiftId, Instant receivedAt) {
        Emergency e = emergencyRepository.save(Emergency.activateByDriver(
            id, owner.getId(), driver.getId(), bus.getId(), shiftId, route.getId(), null,
            receivedAt.minusSeconds(5), at(receivedAt)));
        emergencyIds.add(id);
        return e;
    }

    private Emergency emergency(Company owner, Bus bus, Long shiftId, Instant receivedAt) {
        return emergency(UUID.randomUUID().toString(), owner, bus, shiftId, receivedAt);
    }

    private void startAttention(Emergency created, Instant now) {
        Emergency e = emergencyRepository.findById(created.getId()).orElseThrow();
        e.startAttention(99L, now);
        emergencyRepository.save(e);
    }

    private void close(Emergency created, Instant now) {
        Emergency e = emergencyRepository.findById(created.getId()).orElseThrow();
        e.startAttention(99L, now);
        e.close("resolved", "ok", now.plusSeconds(1));
        emergencyRepository.save(e);
    }

    private BusOccupancy countedOccupancy(Bus bus, Long shiftId, int entries, Instant heartbeat) {
        BusOccupancy o = BusOccupancy.create(bus.getCompanyId(), bus.getId(), 1L);
        o.initialize(UUID.randomUUID().toString(), shiftId, 0, NOW.minusSeconds(600), NOW);
        for (int i = 1; i <= entries; i++) {
            o.apply(shiftId, OccupancyEventType.ENTRY, i, NOW.minusSeconds(500 - i));
        }
        if (heartbeat != null) {
            o.recordHeartbeat(heartbeat);
        }
        BusOccupancy saved = occupancyRepository.save(o);
        occupancies.add(saved);
        return saved;
    }

    private PassengerRequestGroup openGroup(Company owner, Bus bus, Long shiftId, Instant startedAt) {
        PassengerRequestGroup g = groupRepository.save(
            PassengerRequestGroup.open(owner.getId(), bus.getId(), shiftId, startedAt));
        groups.add(g);
        return g;
    }

    private BusOverview busOf(FleetOverviewResult result, Bus bus) {
        return result.buses().stream().filter(b -> b.busId().equals(bus.getId())).findFirst().orElseThrow();
    }

    @Test
    void busWithShiftPositionAndEmergencies_isCompleteAndBusWithNothingIsBare() {
        Emergency active = emergency(company, busA, shiftA.getId(), NOW.minus(Duration.ofMinutes(10)));
        Emergency attended = emergency(company, busA, shiftA.getId(), NOW.minus(Duration.ofMinutes(5)));
        startAttention(attended, NOW.minus(Duration.ofMinutes(4)));

        FleetOverviewResult result = service.getOverview();

        assertEquals(NOW, result.generatedAt());
        assertEquals(List.of(busA.getId(), busB.getId()), result.buses().stream().map(BusOverview::busId).toList());

        BusOverview a = busOf(result, busA);
        assertEquals("OV-A", a.plate());
        assertTrue(a.enabled());
        assertNull(a.capacity());
        assertEquals(new FleetOverviewResult.ShiftOverview(shiftA.getId(), shiftA.getStartedAt()), a.shift());
        assertEquals(new FleetOverviewResult.RouteOverview("Overview Route", "Origin", "Destination"), a.route());
        assertEquals(new FleetOverviewResult.DriverOverview("Overview Driver"), a.driver());
        assertEquals(new FleetOverviewResult.LocationOverview(LocationStatus.CURRENT, -12.05, -77.04, 8.0,
            NOW.minusSeconds(30)), a.location());
        assertEquals(List.of(
            new EmergencyOverview(attended.getId(), "DRIVER", "CRITICAL", "IN_PROGRESS", shiftA.getId(),
                NOW.minus(Duration.ofMinutes(5)).minusSeconds(5), NOW.minus(Duration.ofMinutes(5)),
                NOW.minus(Duration.ofMinutes(4))),
            new EmergencyOverview(active.getId(), "DRIVER", "CRITICAL", "ACTIVE", shiftA.getId(),
                NOW.minus(Duration.ofMinutes(10)).minusSeconds(5), NOW.minus(Duration.ofMinutes(10)), null)),
            a.emergencies());

        BusOverview b = busOf(result, busB);
        assertNull(b.shift());
        assertNull(b.route());
        assertNull(b.driver());
        assertEquals(new FleetOverviewResult.LocationOverview(LocationStatus.UNAVAILABLE, null, null, null, null),
            b.location());
        assertTrue(b.emergencies().isEmpty());
        assertTrue(a.passengerGroups().isEmpty());
        assertTrue(b.passengerGroups().isEmpty());
    }

    @Test
    void busWithoutAnyCountHasAnUnavailableOccupancyWithNoNumbers() {
        BusOverview a = busOf(service.getOverview(), busA);

        assertEquals(new OccupancyOverview("UNAVAILABLE", "NOT_INITIALIZED", null, null, null, null, null),
            a.occupancy());
    }

    @Test
    void currentOccupancyCarriesCountCapacityLoadAndTimes() {
        Bus stored = busRepository.findById(busA.getId()).orElseThrow();
        stored.recordCapacity(2, "TECH-2", 1L, at(NOW));
        busRepository.save(stored);
        BusOccupancy o = countedOccupancy(busA, shiftA.getId(), 3, NOW.minusSeconds(30));

        OccupancyOverview occupancy = busOf(service.getOverview(), busA).occupancy();

        assertEquals(new OccupancyOverview("CURRENT", null, 3, 2, "OVER_CAPACITY", o.getLastCountAt(),
            NOW.minusSeconds(30)), occupancy);
    }

    @Test
    void occupancyWithAnOldHeartbeatIsStaleButKeepsTheCount() {
        countedOccupancy(busA, shiftA.getId(), 1, NOW.minusSeconds(121));

        OccupancyOverview occupancy = busOf(service.getOverview(), busA).occupancy();

        assertEquals("STALE", occupancy.status());
        assertEquals(1, occupancy.count());
        assertEquals("CAPACITY_UNKNOWN", occupancy.load());
    }

    @Test
    void occupancyCountedOnAnotherShiftIsUnavailable() {
        countedOccupancy(busA, shiftA.getId() + 1_000_000L, 2, NOW);

        OccupancyOverview occupancy = busOf(service.getOverview(), busA).occupancy();

        assertEquals("UNAVAILABLE", occupancy.status());
        assertNull(occupancy.count());
        assertNull(occupancy.load());
    }

    @Test
    void openGroupsOfABusAreListedOldestWindowFirstWithoutIdentities() {
        PassengerRequestGroup older = openGroup(company, busA, shiftA.getId(), NOW.minusSeconds(200));
        PassengerRequestGroup newer = openGroup(company, busA, shiftA.getId() + 1_000_000L, NOW.minusSeconds(100));
        PassengerRequestGroup stored = groupRepository.findById(newer.getId()).orElseThrow();
        stored.moveToAwaitingApproval(NOW.minusSeconds(50));
        groupRepository.save(stored);

        BusOverview a = busOf(service.getOverview(), busA);

        assertEquals(List.of(
            new PassengerGroupOverview("COLLECTING", 0, 0, older.getWindowStartedAt(), older.getWindowExpiresAt(),
                null, null),
            new PassengerGroupOverview("AWAITING_APPROVAL", 0, 0, newer.getWindowStartedAt(),
                newer.getWindowExpiresAt(), NOW.minusSeconds(50), null)), a.passengerGroups());
        assertTrue(busOf(service.getOverview(), busB).passengerGroups().isEmpty());
    }

    @Test
    void groupOfAnotherCompanyIsNotShownOnAnyBus() {
        Bus foreign = saveBus(otherCompany, "OV-Z");
        openGroup(otherCompany, foreign, 1L, NOW.minusSeconds(60));

        FleetOverviewResult result = service.getOverview();

        assertTrue(result.buses().stream().allMatch(b -> b.passengerGroups().isEmpty()));
    }

    @Test
    void groupOfADisabledBusWithoutEmergenciesIsNotShown() {
        busB.disable();
        busRepository.save(busB);
        openGroup(company, busB, 1L, NOW.minusSeconds(60));

        FleetOverviewResult result = service.getOverview();

        assertEquals(List.of(busA.getId()), result.buses().stream().map(BusOverview::busId).toList());
    }

    @Test
    void busesAreOrderedByPlate() {
        assertEquals(List.of("OV-A", "OV-B"), service.getOverview().buses().stream().map(BusOverview::plate).toList());
    }

    @Test
    void busOfAnotherCompanyAndItsEmergenciesNeverAppear() {
        Bus foreign = saveBus(otherCompany, "OV-X");
        emergency(otherCompany, foreign, 1L, NOW.minusSeconds(60));

        FleetOverviewResult result = service.getOverview();

        assertEquals(2, result.buses().size());
        assertTrue(result.buses().stream().noneMatch(b -> b.busId().equals(foreign.getId())));
        assertTrue(result.buses().stream().allMatch(b -> b.emergencies().isEmpty()));
    }

    @Test
    void openEmergencyOfABusOutsideTheCompanyList_isIgnored() {
        Bus foreign = saveBus(otherCompany, "OV-Y");
        emergency(company, foreign, 1L, NOW.minusSeconds(60));

        FleetOverviewResult result = service.getOverview();

        assertEquals(2, result.buses().size());
        assertTrue(result.buses().stream().noneMatch(b -> b.busId().equals(foreign.getId())));
        assertTrue(result.buses().stream().allMatch(b -> b.emergencies().isEmpty()));
    }

    @Test
    void closedEmergency_doesNotAppear() {
        close(emergency(company, busA, shiftA.getId(), NOW.minusSeconds(600)), NOW.minusSeconds(300));

        assertTrue(busOf(service.getOverview(), busA).emergencies().isEmpty());
    }

    @Test
    void disabledBusWithoutEmergencies_isExcluded() {
        busB.disable();
        busRepository.save(busB);

        List<Long> ids = service.getOverview().buses().stream().map(BusOverview::busId).toList();

        assertEquals(List.of(busA.getId()), ids);
    }

    @Test
    void disabledBusWithAnOpenEmergency_isIncluded() {
        busB.disable();
        busRepository.save(busB);
        Emergency e = emergency(company, busB, 1L, NOW.minusSeconds(60));

        FleetOverviewResult result = service.getOverview();

        BusOverview b = busOf(result, busB);
        assertEquals(false, b.enabled());
        assertEquals(List.of(e.getId()), b.emergencies().stream().map(EmergencyOverview::id).toList());
    }

    @Test
    void openEmergencyOfABusWhoseShiftIsClosed_isStillShown() {
        Emergency e = emergency(company, busA, shiftA.getId(), NOW.minusSeconds(120));
        DriverShift stored = driverShiftRepository.findById(shiftA.getId()).orElseThrow();
        stored.close(NOW.minusSeconds(60), null, null);
        driverShiftRepository.save(stored);

        BusOverview a = busOf(service.getOverview(), busA);

        assertNull(a.shift());
        assertEquals(List.of(e.getId()), a.emergencies().stream().map(EmergencyOverview::id).toList());
        assertEquals(shiftA.getId(), a.emergencies().get(0).shiftId());
    }

    @Test
    void emergenciesWithTheSameReceivedAt_areOrderedByIdAscending() {
        Instant sameInstant = NOW.minusSeconds(90);
        emergency("ov-e-bbb", company, busA, shiftA.getId(), sameInstant);
        emergency("ov-e-aaa", company, busA, shiftA.getId(), sameInstant);
        emergency("ov-e-ccc", company, busA, shiftA.getId(), sameInstant.plusSeconds(1));

        List<String> ids = busOf(service.getOverview(), busA).emergencies().stream()
            .map(EmergencyOverview::id).toList();

        assertEquals(List.of("ov-e-ccc", "ov-e-aaa", "ov-e-bbb"), ids);
    }

    @Test
    void staleAndUnavailableLocations_areNotPresentedAsCurrent() {
        savePosition(busB, -13.0, -76.0, 4.0, NOW.minusSeconds(181));

        FleetOverviewResult result = service.getOverview();

        assertEquals(LocationStatus.CURRENT, busOf(result, busA).location().status());
        assertEquals(new FleetOverviewResult.LocationOverview(LocationStatus.STALE, -13.0, -76.0, 4.0,
            NOW.minusSeconds(181)), busOf(result, busB).location());
    }

    @Test
    void capacityIsCarriedFromTheBus() {
        Bus stored = busRepository.findById(busA.getId()).orElseThrow();
        stored.recordCapacity(42, "TECH-42", 1L, at(NOW));
        busRepository.save(stored);

        assertEquals(42, busOf(service.getOverview(), busA).capacity());
    }

    @Test
    void companyWithoutBuses_givesAnEmptyList() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(2L, "SUPERVISOR", otherCompany.getId()));

        FleetOverviewResult result = service.getOverview();

        assertTrue(result.buses().isEmpty());
        assertEquals(NOW, result.generatedAt());
    }

    @Test
    void nullCompanyId_isForbiddenWithFleetAccessDenied() {
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(3L, "SUPERVISOR", null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class, () -> service.getOverview());

        assertEquals("FLEET_ACCESS_DENIED", ex.code());
        assertEquals("access denied", ex.getMessage());
    }

    @Test
    void everyBatchReadIsIssuedOnceRegardlessOfTheNumberOfBuses() {
        saveBus(company, "OV-C");
        saveBus(company, "OV-D");

        service.getOverview();

        verify(fleetFacade, times(1)).listBusesOfCompany(any());
        verify(fleetFacade, times(1)).findDriversByIds(any());
        verify(fleetFacade, times(1)).findRoutesByIds(any());
        verify(tripFacade, times(1)).findActiveShiftsByBusIds(any());
        verify(tripFacade, times(1)).findLastPositionsByBusIds(any());
        verify(tripFacade, times(1)).findOccupancyByBusIds(any());
        verify(safetycaseFacade, times(1)).findOpenEmergenciesOfCompany(any());
        verify(safetycaseFacade, times(1)).findOpenGroupsOfCompany(any());
    }
}
