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
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
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

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class RecordLocationEventServiceTest {

    private static final Instant T1 = Instant.parse("2030-06-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-06-01T16:00:00Z");
    private static final double LAT = -12.046;
    private static final double LON = -77.042;
    private static final double ACC = 10.0;

    @Autowired RecordLocationEventCommandService service;
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

    Company company;
    Bus bus;
    Driver driver;
    Route route;
    ShiftAssignment assignment;
    DriverShift shift;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("RLE Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "RLE-BUS01", () -> "rle-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 700L, "RLE Driver",
            () -> "RLE-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "RLE Route", "A", "B"));
        assignment = assignmentRepository.save(
            ShiftAssignment.create(driver.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        shift = driverShiftRepository.save(
            DriverShift.start(assignment.getId(), driver.getId(), bus.getId(), route.getId(), clock));
        mockDriver(700L);
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

    private void mockDriver(long userId) {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(userId, "DRIVER", company.getId()));
    }

    private RecordLocationEventCommand cmd(String eventId, Instant capturedAt) {
        return new RecordLocationEventCommand(eventId, shift.getId(), null,
            capturedAt, ACC, LAT, LON);
    }

    private String uuid() { return UUID.randomUUID().toString(); }

    // --- US18 S1: valid event stored, position updated ---

    @Test
    void record_validFirstEvent_storesEventAndUpdatesPosition() {
        String eventId = uuid();
        Instant capturedAt = Instant.now(clock).minusSeconds(10);

        RecordLocationEventResult result = service.record(cmd(eventId, capturedAt));

        assertFalse(result.duplicate());
        assertTrue(result.currentPositionUpdated());
        assertEquals(eventId, result.eventId());
        assertEquals(shift.getId(), result.shiftId());
        assertEquals(bus.getId(), result.busId());
        assertEquals(1L, locationEventRepository.count());
        assertTrue(vehicleLocationRepository.findByBusId(bus.getId()).isPresent());
    }

    @Test
    void record_identicalRetry_returns200WithoutDuplication() {
        String eventId = uuid();
        Instant capturedAt = Instant.now(clock).minusSeconds(10);

        RecordLocationEventResult first = service.record(cmd(eventId, capturedAt));
        RecordLocationEventResult second = service.record(cmd(eventId, capturedAt));

        assertFalse(first.duplicate());
        assertTrue(second.duplicate());
        assertEquals(first.eventId(), second.eventId());
        assertEquals(first.receivedAt(), second.receivedAt());
        assertEquals(1L, locationEventRepository.count());
    }

    @Test
    void record_olderAfterNewer_storedButPositionUnchanged() {
        Instant newer = Instant.now(clock).minusSeconds(5);
        Instant older = Instant.now(clock).minusSeconds(30);

        service.record(cmd(uuid(), newer));
        RecordLocationEventResult olderResult = service.record(cmd(uuid(), older));

        assertFalse(olderResult.duplicate());
        assertFalse(olderResult.currentPositionUpdated());
        assertEquals(2L, locationEventRepository.count());
        var vl = vehicleLocationRepository.findByBusId(bus.getId()).orElseThrow();
        // position stays with the newer capturedAt
        assertTrue(!vl.getCapturedAt().isBefore(newer.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)));
    }

    @Test
    void record_sameEventIdDifferentPayload_throwsEventIdReused() {
        String eventId = uuid();
        Instant t = Instant.now(clock).minusSeconds(10);
        service.record(cmd(eventId, t));

        ConflictException ex = assertThrows(ConflictException.class, () ->
            service.record(new RecordLocationEventCommand(
                eventId, shift.getId(), null, t, ACC + 5.0, LAT, LON)));

        assertEquals("EVENT_ID_REUSED", ex.code());
        assertEquals(1L, locationEventRepository.count());
    }

    @Test
    void record_closedShift_accepted() throws Exception {
        Field f = DriverShift.class.getDeclaredField("status");
        f.setAccessible(true);
        f.set(shift, ShiftStatus.CLOSED);
        driverShiftRepository.save(shift);

        RecordLocationEventResult result = service.record(cmd(uuid(), Instant.now(clock).minusSeconds(10)));

        assertFalse(result.duplicate());
        assertEquals(1L, locationEventRepository.count());
    }

    // --- US18 S2: rejection cases ---

    @Test
    void record_invalidLatitude_throwsInvalidCoordinates() {
        var ex = assertThrows(RuleViolationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), shift.getId(), null, Instant.now(clock).minusSeconds(10), ACC, 90.1, LON)));
        assertEquals("INVALID_COORDINATES", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_invalidLongitude_throwsInvalidCoordinates() {
        var ex = assertThrows(RuleViolationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), shift.getId(), null, Instant.now(clock).minusSeconds(10), ACC, LAT, 180.1)));
        assertEquals("INVALID_COORDINATES", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_negativeAccuracy_throwsInvalidAccuracy() {
        var ex = assertThrows(RuleViolationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), shift.getId(), null, Instant.now(clock).minusSeconds(10), -1.0, LAT, LON)));
        assertEquals("INVALID_ACCURACY", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_capturedAt6MinutesInFuture_throwsInvalidCaptureTime() {
        Instant future = Instant.now(clock).plusSeconds(360);
        var ex = assertThrows(RuleViolationException.class, () ->
            service.record(cmd(uuid(), future)));
        assertEquals("INVALID_CAPTURE_TIME", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_busIdMismatch_throwsBusShiftMismatch() {
        Long wrongBusId = bus.getId() + 999L;
        var ex = assertThrows(RuleViolationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), shift.getId(), wrongBusId, Instant.now(clock).minusSeconds(10), ACC, LAT, LON)));
        assertEquals("BUS_SHIFT_MISMATCH", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_anotherDriverShift_throws403() {
        Driver other = driverRepository.save(Driver.create(
            company.getId(), 701L, "RLE Other Driver", () -> "RLE-DRV-QR-002",
            Duration.ofDays(365), clock));
        ShiftAssignment otherAssignment = assignmentRepository.save(
            ShiftAssignment.create(other.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherAssignment.getId(), other.getId(), bus.getId(), route.getId(), clock));

        // caller is driver 700, but otherShift belongs to driver 701
        var ex = assertThrows(ForbiddenOperationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), otherShift.getId(), null, Instant.now(clock).minusSeconds(10), ACC, LAT, LON)));
        assertEquals("LOCATION_SOURCE_NOT_AUTHORIZED", ex.code());
        assertEquals(0L, locationEventRepository.count());
    }

    @Test
    void record_nonExistentShift_throws403WithSameBodyAsAnotherDriver() {
        Driver other = driverRepository.save(Driver.create(
            company.getId(), 702L, "RLE Second Other", () -> "RLE-DRV-QR-003",
            Duration.ofDays(365), clock));
        ShiftAssignment otherAssignment = assignmentRepository.save(
            ShiftAssignment.create(other.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
        DriverShift otherShift = driverShiftRepository.save(
            DriverShift.start(otherAssignment.getId(), other.getId(), bus.getId(), route.getId(), clock));

        ForbiddenOperationException fromAnother = assertThrows(ForbiddenOperationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), otherShift.getId(), null, Instant.now(clock).minusSeconds(10), ACC, LAT, LON)));

        ForbiddenOperationException fromMissing = assertThrows(ForbiddenOperationException.class, () ->
            service.record(new RecordLocationEventCommand(
                uuid(), 999999L, null, Instant.now(clock).minusSeconds(10), ACC, LAT, LON)));

        assertEquals(fromMissing.code(), fromAnother.code());
        assertEquals(fromMissing.getMessage(), fromAnother.getMessage());
        assertEquals(0L, locationEventRepository.count());
    }
}
