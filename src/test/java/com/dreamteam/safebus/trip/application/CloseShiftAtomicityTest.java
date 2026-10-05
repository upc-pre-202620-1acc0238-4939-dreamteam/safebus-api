package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
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
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.LocationEvent;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.model.VehicleLocation;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CloseShiftAtomicityTest {

    private static final Instant T1 = Instant.parse("2030-05-02T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-05-02T16:00:00Z");

    @Autowired CloseShiftCommandService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean ShiftJourneyClosurePort journeyPort;

    Company company;
    Bus bus;
    Route route;
    Driver driver;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("CA Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "CA-P01", () -> "ca-bus-qr1"));
        route = routeRepository.save(Route.create(company.getId(), "CA Route", "A", "B"));
        driver = driverRepository.save(Driver.create(company.getId(), 9211L, "CA Driver",
            () -> "CA-DRV-QR-001", Duration.ofDays(365), clock));
        ShiftAssignment sa = ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock);
        sa.activate();
        sa = assignmentRepository.save(sa);
        shift = driverShiftRepository.save(
            DriverShift.start(sa.getId(), driver.getId(), bus.getId(), route.getId(), clock));
        VehicleLocation vl = VehicleLocation.empty(bus.getId());
        vl.applyIfNewer(LocationEvent.create("ca-ev-1", shift.getId(), bus.getId(),
            -12.0, -77.0, 5.0, Instant.parse("2030-05-02T15:00:00Z"), clock), clock);
        vehicleLocationRepository.saveAndFlush(vl);
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(9211L, "DRIVER", company.getId()));
    }

    @AfterEach
    void tearDown() {
        vehicleLocationRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    @Test
    void journeyPortFailure_rollsBackShiftAndAssignment() {
        when(journeyPort.endJourneysOfShift(anyLong(), any()))
            .thenThrow(new IllegalStateException("journey closure failed"));

        assertThrows(IllegalStateException.class,
            () -> service.close(new CloseShiftCommand(shift.getId())));

        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.ACTIVE, stored.getStatus());
        assertNull(stored.getClosedAt());
        assertNull(stored.getLastKnownPoint());
        assertNull(stored.getLastKnownCapturedAt());
        assertEquals(AssignmentStatus.ACTIVE,
            assignmentRepository.findById(shift.getAssignmentId()).orElseThrow().getStatus());
    }
}
