package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.fleet.domain.repository.ShiftAssignmentRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import com.dreamteam.safebus.trip.domain.repository.LocationEventRepository;
import com.dreamteam.safebus.trip.domain.repository.VehicleLocationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class GetBusLocationQueryServiceTest {

    private static final Instant T1 = Instant.parse("2030-07-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-07-01T16:00:00Z");

    @Autowired GetBusLocationQueryService service;
    @Autowired RecordLocationEventCommandService recordService;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired LocationEventRepository locationEventRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean PassengerJourneyAccessPort passengerJourneyAccessPort;

    Company company;
    Bus bus;
    Driver driver;
    Route route;
    ShiftAssignment assignment;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("GBL Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "GBL-BUS01", () -> "gbl-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 800L, "GBL Driver",
            () -> "GBL-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "GBL Route", "A", "B"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
    }

    @AfterEach
    void tearDown() {
        vehicleLocationRepository.deleteAll();
        locationEventRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private void postLocation(Instant capturedAt) {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(800L, "DRIVER", company.getId()));
        recordService.record(new RecordLocationEventCommand(
            UUID.randomUUID().toString(), shift.getId(), null,
            capturedAt, 10.0, -12.046, -77.042));
    }

    @Test
    void getLocation_supervisor_matchingCompany_returns200() {
        postLocation(Instant.now(clock).minusSeconds(10));
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId()));

        BusLocationResult result = service.getLocation(bus.getId());

        assertEquals(bus.getId(), result.busId());
        assertEquals(-12.046, result.latitude());
        assertEquals(-77.042, result.longitude());
        assertNotNull(result.capturedAt());
        assertEquals(10.0, result.accuracyMeters());
    }

    @Test
    void getLocation_noLocationYet_throws404() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId()));

        NotFoundException ex = assertThrows(NotFoundException.class,
            () -> service.getLocation(bus.getId()));
        assertEquals("LOCATION_UNAVAILABLE", ex.code());
    }

    @Test
    void getLocation_supervisor_foreignBus_throws403() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId() + 999L));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.getLocation(bus.getId()));
        assertEquals("BUS_ACCESS_DENIED", ex.code());
    }

    @Test
    void getLocation_supervisor_nonExistentBus_throws403SameBodyAsForeignBus() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId()));

        ForbiddenOperationException fromForeign = assertThrows(ForbiddenOperationException.class, () -> {
            when(currentUserProvider.current())
                .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId() + 999L));
            service.getLocation(bus.getId());
        });

        ForbiddenOperationException fromMissing = assertThrows(ForbiddenOperationException.class, () -> {
            when(currentUserProvider.current())
                .thenReturn(new AuthenticatedUser(900L, "SUPERVISOR", company.getId()));
            service.getLocation(999999L);
        });

        assertEquals(fromMissing.code(), fromForeign.code());
        assertEquals(fromMissing.getMessage(), fromForeign.getMessage());
    }

    @Test
    void getLocation_passenger_withActiveJourney_returns200() {
        postLocation(Instant.now(clock).minusSeconds(10));
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(1000L, "PASSENGER", null));
        when(passengerJourneyAccessPort.hasActiveJourneyOnBus(eq(1000L), eq(bus.getId())))
            .thenReturn(true);

        BusLocationResult result = service.getLocation(bus.getId());

        assertEquals(bus.getId(), result.busId());
    }

    @Test
    void getLocation_passenger_noActiveJourney_throws403() {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(1000L, "PASSENGER", null));
        when(passengerJourneyAccessPort.hasActiveJourneyOnBus(any(), any()))
            .thenReturn(false);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.getLocation(bus.getId()));
        assertEquals("BUS_ACCESS_DENIED", ex.code());
    }
}
