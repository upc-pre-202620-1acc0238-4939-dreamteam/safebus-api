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
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CloseShiftCommandServiceTest {

    private static final Instant T1 = Instant.parse("2030-05-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-05-01T16:00:00Z");
    private static final Instant NOW = Instant.parse("2030-05-01T15:30:00.789123Z");
    private static final Instant NOW_MILLIS = Instant.parse("2030-05-01T15:30:00.789Z");

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    @Autowired CloseShiftCommandService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired VehicleLocationRepository vehicleLocationRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;

    Company company;
    Bus bus;
    Route route;
    Driver driver;
    Driver otherDriver;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("CS Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "CS-P01", () -> "cs-bus-qr1"));
        route = routeRepository.save(Route.create(company.getId(), "CS Route", "A", "B"));
        driver = driverRepository.save(Driver.create(company.getId(), 9201L, "CS Driver",
            () -> "CS-DRV-QR-001", Duration.ofDays(365), clock));
        otherDriver = driverRepository.save(Driver.create(company.getId(), 9202L, "CS Other Driver",
            () -> "CS-DRV-QR-002", Duration.ofDays(365), clock));
        mockUser(9201L);
    }

    @AfterEach
    void tearDown() {
        journeyRepository.deleteAll();
        vehicleLocationRepository.deleteAll();
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private void mockUser(long userId) {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(userId, "DRIVER", company.getId()));
    }

    private DriverShift activeShift(Driver d) {
        ShiftAssignment sa = ShiftAssignment.create(d.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock);
        sa.activate();
        sa = assignmentRepository.save(sa);
        return driverShiftRepository.save(
            DriverShift.start(sa.getId(), d.getId(), bus.getId(), route.getId(), clock));
    }

    private void busPosition(double lat, double lon, Instant capturedAt) {
        VehicleLocation vl = VehicleLocation.empty(bus.getId());
        LocationEvent ev = LocationEvent.create("cs-ev-" + capturedAt.toEpochMilli(), 1L, bus.getId(),
            lat, lon, 5.0, capturedAt, clock);
        vl.applyIfNewer(ev, clock);
        vehicleLocationRepository.saveAndFlush(vl);
    }

    private AssignmentStatus assignmentStatus(DriverShift s) {
        return assignmentRepository.findById(s.getAssignmentId()).orElseThrow().getStatus();
    }

    @Test
    void owner_closesShiftWithClockTimeAndBusLastPosition() {
        DriverShift shift = activeShift(driver);
        Instant capturedAt = Instant.parse("2030-05-01T15:29:00Z");
        busPosition(-12.046, -77.042, capturedAt);

        DriverShift result = service.close(new CloseShiftCommand(shift.getId()));

        assertEquals(ShiftStatus.CLOSED, result.getStatus());
        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, stored.getStatus());
        assertEquals(NOW_MILLIS, stored.getClosedAt());
        assertEquals(-12.046, stored.getLastKnownPoint().getLatitude());
        assertEquals(-77.042, stored.getLastKnownPoint().getLongitude());
        assertEquals(capturedAt, stored.getLastKnownCapturedAt());
    }

    @Test
    void owner_closesAssignmentAndEndsOnlyThatShiftsJourneys() {
        DriverShift shift = activeShift(driver);
        PassengerJourney j1 = journeyRepository.saveAndFlush(
            PassengerJourney.start(9301L, bus.getId(), shift.getId(), clock));
        PassengerJourney j2 = journeyRepository.saveAndFlush(
            PassengerJourney.start(9302L, bus.getId(), shift.getId(), clock));
        PassengerJourney foreign = journeyRepository.saveAndFlush(
            PassengerJourney.start(9303L, bus.getId(), shift.getId() + 1000, clock));

        service.close(new CloseShiftCommand(shift.getId()));

        assertEquals(AssignmentStatus.CLOSED, assignmentStatus(shift));
        for (Long id : new Long[] {j1.getId(), j2.getId()}) {
            PassengerJourney j = journeyRepository.findById(id).orElseThrow();
            assertEquals(JourneyStatus.ENDED, j.getStatus());
            assertEquals(JourneyEndReason.SHIFT_CLOSED, j.getEndReason());
            assertEquals(NOW_MILLIS, j.getEndedAt());
        }
        assertEquals(JourneyStatus.ACTIVE, journeyRepository.findById(foreign.getId()).orElseThrow().getStatus());
    }

    @Test
    void busWithoutPositionYet_closesWithNullPosition() {
        DriverShift shift = activeShift(driver);

        service.close(new CloseShiftCommand(shift.getId()));

        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, stored.getStatus());
        assertEquals(NOW_MILLIS, stored.getClosedAt());
        assertNull(stored.getLastKnownPoint());
        assertNull(stored.getLastKnownCapturedAt());
    }

    @Test
    void busWithEmptyVehicleLocationRow_closesWithNullPosition() {
        DriverShift shift = activeShift(driver);
        vehicleLocationRepository.saveAndFlush(VehicleLocation.empty(bus.getId()));

        service.close(new CloseShiftCommand(shift.getId()));

        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertNull(stored.getLastKnownPoint());
        assertNull(stored.getLastKnownCapturedAt());
    }

    @Test
    void alreadyClosedShift_returnsUnchangedAndTouchesNothingElse() {
        DriverShift shift = activeShift(driver);
        Instant originalClosedAt = Instant.parse("2030-05-01T12:00:00Z");
        shift.close(originalClosedAt, null, null);
        driverShiftRepository.saveAndFlush(shift);
        // assignment and journey deliberately left ACTIVE: a repeated close must not touch them
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(9304L, bus.getId(), shift.getId(), clock));
        busPosition(1.0, 2.0, Instant.parse("2030-05-01T15:00:00Z"));

        DriverShift result = service.close(new CloseShiftCommand(shift.getId()));

        assertEquals(ShiftStatus.CLOSED, result.getStatus());
        assertEquals(originalClosedAt, result.getClosedAt());
        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(originalClosedAt, stored.getClosedAt());
        assertNull(stored.getLastKnownPoint());
        assertEquals(AssignmentStatus.ACTIVE, assignmentStatus(shift));
        assertEquals(JourneyStatus.ACTIVE, journeyRepository.findById(journey.getId()).orElseThrow().getStatus());
    }

    @Test
    void anotherDriversShift_andMissingShift_throwSameForbiddenAndChangeNothing() {
        DriverShift othersShift = activeShift(otherDriver);
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(9305L, bus.getId(), othersShift.getId(), clock));

        ForbiddenOperationException foreign = assertThrows(ForbiddenOperationException.class,
            () -> service.close(new CloseShiftCommand(othersShift.getId())));
        ForbiddenOperationException missing = assertThrows(ForbiddenOperationException.class,
            () -> service.close(new CloseShiftCommand(999999L)));

        assertEquals("SHIFT_NOT_AUTHORIZED", foreign.code());
        assertEquals(missing.code(), foreign.code());
        assertEquals(missing.getMessage(), foreign.getMessage());
        DriverShift stored = driverShiftRepository.findById(othersShift.getId()).orElseThrow();
        assertEquals(ShiftStatus.ACTIVE, stored.getStatus());
        assertNull(stored.getClosedAt());
        assertEquals(AssignmentStatus.ACTIVE, assignmentStatus(othersShift));
        assertEquals(JourneyStatus.ACTIVE, journeyRepository.findById(journey.getId()).orElseThrow().getStatus());
    }

    @Test
    void callerWithoutDriverProfile_throwsSameForbidden() {
        DriverShift shift = activeShift(driver);
        mockUser(424242L);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> service.close(new CloseShiftCommand(shift.getId())));

        assertEquals("SHIFT_NOT_AUTHORIZED", ex.code());
        assertEquals("shift not authorized for this driver", ex.getMessage());
        assertEquals(ShiftStatus.ACTIVE, driverShiftRepository.findById(shift.getId()).orElseThrow().getStatus());
    }
}
