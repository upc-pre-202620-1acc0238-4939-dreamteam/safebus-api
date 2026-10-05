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
import com.dreamteam.safebus.fleetmonitoring.domain.LocationStatus;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.interfaces.acl.SafetycaseContextFacade;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
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
        verify(safetycaseFacade, times(1)).findOpenEmergenciesOfCompany(any());
    }
}
