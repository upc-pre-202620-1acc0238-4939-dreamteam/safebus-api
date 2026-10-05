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
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class ActivateShiftCommandServiceTest {

    private static final Instant T1 = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-01-01T16:00:00Z");

    @Autowired ActivateShiftCommandService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired DriverShiftRepository driverShiftRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;

    Company company;
    Bus bus;
    Route route;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("AS Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "AS-P01", () -> "as-bus-qr1"));
        route = routeRepository.save(Route.create(company.getId(), "AS Route", "A", "B"));
    }

    @AfterEach
    void tearDown() {
        driverShiftRepository.deleteAll();
        assignmentRepository.deleteAll();
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private Driver validDriver(long userAccountId, String qr) {
        return driverRepository.save(
            Driver.create(company.getId(), userAccountId, "AS Driver " + userAccountId,
                () -> qr, Duration.ofDays(365), clock));
    }

    private ShiftAssignment assignedShift(Driver d) {
        return assignmentRepository.save(
            ShiftAssignment.create(d.getId(), bus.getId(), route.getId(), T1, T2, 1L, clock));
    }

    private void mockUser(long userId) {
        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(userId, "DRIVER", company.getId()));
    }

    @Test
    void activate_valid_createsDriverShift() {
        Driver d = validDriver(300L, "AS-QR-001");
        ShiftAssignment sa = assignedShift(d);
        mockUser(300L);

        DriverShift result = service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-001"));

        assertNotNull(result.getId());
        assertEquals(sa.getId(), result.getAssignmentId());
        assertEquals(d.getId(), result.getDriverId());
        assertEquals(bus.getId(), result.getBusId());
        assertEquals(route.getId(), result.getRouteId());
        assertEquals(ShiftStatus.ACTIVE, result.getStatus());
        assertNotNull(result.getStartedAt());
        assertNull(result.getClosedAt());
        assertEquals(1L, driverShiftRepository.count());
    }

    @Test
    void activate_unknownCredential_throwsCredentialInvalidAndNoDriverShift() {
        mockUser(300L);
        long before = driverShiftRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(1L, "NONEXISTENT-QR")));

        assertEquals("CREDENTIAL_INVALID", ex.code());
        assertEquals(before, driverShiftRepository.count());
    }

    @Test
    void activate_credentialOfAnotherDriver_throwsCredentialNotOwned() {
        Driver other = validDriver(301L, "AS-QR-002");
        assignedShift(other);
        mockUser(999L); // authenticated as a different user

        long before = driverShiftRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(1L, "AS-QR-002")));

        assertEquals("CREDENTIAL_NOT_OWNED", ex.code());
        assertEquals(before, driverShiftRepository.count());
    }

    @Test
    void activate_disabledDriver_throwsCredentialDisabledAndNoDriverShift() {
        Driver d = validDriver(302L, "AS-QR-003");
        d.disable();
        driverRepository.save(d);
        ShiftAssignment sa = assignedShift(d);
        mockUser(302L);

        long before = driverShiftRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-003")));

        assertEquals("CREDENTIAL_DISABLED", ex.code());
        assertEquals(before, driverShiftRepository.count());
        ShiftAssignment unchanged = assignmentRepository.findById(sa.getId()).orElseThrow();
        assertEquals(com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.ASSIGNED,
            unchanged.getStatus());
    }

    @Test
    void activate_expiredCredential_throwsCredentialExpiredAndNoDriverShift() {
        Driver d = driverRepository.save(
            Driver.create(company.getId(), 303L, "Expired Driver",
                () -> "AS-QR-004", Duration.ofDays(-1), clock));
        ShiftAssignment sa = assignedShift(d);
        mockUser(303L);

        long before = driverShiftRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-004")));

        assertEquals("CREDENTIAL_EXPIRED", ex.code());
        assertEquals(before, driverShiftRepository.count());
        ShiftAssignment unchanged = assignmentRepository.findById(sa.getId()).orElseThrow();
        assertEquals(com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.ASSIGNED,
            unchanged.getStatus());
    }

    @Test
    void activate_assignmentNotFound_throwsAndNoDriverShift() {
        Driver d = validDriver(304L, "AS-QR-005");
        mockUser(304L);

        long before = driverShiftRepository.count();

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(999999L, "AS-QR-005")));

        assertEquals("ASSIGNMENT_NOT_FOUND", ex.code());
        assertEquals(before, driverShiftRepository.count());
    }

    @Test
    void activate_assignmentOfAnotherDriver_sameBodyAsNotFound() {
        Driver d1 = validDriver(305L, "AS-QR-006");
        Driver d2 = validDriver(306L, "AS-QR-007");
        ShiftAssignment d2sa = assignedShift(d2);
        mockUser(305L);

        long before = driverShiftRepository.count();

        RuleViolationException fromOtherDriver = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(d2sa.getId(), "AS-QR-006")));
        RuleViolationException fromMissing = assertThrows(RuleViolationException.class,
            () -> service.activate(new ActivateShiftCommand(999999L, "AS-QR-006")));

        assertEquals(fromMissing.code(), fromOtherDriver.code());
        assertEquals(fromMissing.getMessage(), fromOtherDriver.getMessage());
        assertEquals(before, driverShiftRepository.count());

        ShiftAssignment unchanged = assignmentRepository.findById(d2sa.getId()).orElseThrow();
        assertEquals(com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.ASSIGNED,
            unchanged.getStatus());
    }

    @Test
    void activate_assignmentAlreadyActive_throwsConflictAndNoExtraShift() {
        Driver d = validDriver(307L, "AS-QR-008");
        ShiftAssignment sa = assignedShift(d);
        mockUser(307L);

        service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-008"));
        long afterFirst = driverShiftRepository.count();

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-008")));

        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
        assertEquals(afterFirst, driverShiftRepository.count());
        ShiftAssignment reloaded = assignmentRepository.findById(sa.getId()).orElseThrow();
        assertEquals(com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.ACTIVE,
            reloaded.getStatus());
    }

    @Test
    void activate_assignmentAlreadyClosed_throwsConflictAndNoDriverShift() {
        Driver d = validDriver(308L, "AS-QR-009");
        ShiftAssignment sa = assignedShift(d);
        try {
            java.lang.reflect.Field f = ShiftAssignment.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(sa, com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.CLOSED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assignmentRepository.save(sa);
        mockUser(308L);

        long before = driverShiftRepository.count();

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-009")));

        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
        assertEquals(before, driverShiftRepository.count());
        ShiftAssignment unchanged = assignmentRepository.findById(sa.getId()).orElseThrow();
        assertEquals(com.dreamteam.safebus.fleet.domain.model.AssignmentStatus.CLOSED,
            unchanged.getStatus());
    }

    @Test
    void activate_dataIntegrityViolation_throwsConflictAssignmentNotAvailable() {
        Driver d = validDriver(309L, "AS-QR-010");
        ShiftAssignment sa = assignedShift(d);
        // Pre-insert a DriverShift row with this assignmentId to trigger the unique constraint
        driverShiftRepository.save(DriverShift.start(sa.getId(), d.getId(), bus.getId(), route.getId(), clock));
        mockUser(309L);

        long before = driverShiftRepository.count();

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.activate(new ActivateShiftCommand(sa.getId(), "AS-QR-010")));

        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
        assertEquals(before, driverShiftRepository.count());
    }
}
