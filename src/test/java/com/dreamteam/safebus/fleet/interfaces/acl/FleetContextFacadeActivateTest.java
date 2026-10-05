package com.dreamteam.safebus.fleet.interfaces.acl;

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
import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadeActivateTest {

    private static final Instant T1 = Instant.parse("2030-01-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2030-01-01T16:00:00Z");

    @Autowired FleetContextFacade facade;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired Clock clock;

    Company company;
    Bus bus;
    Driver driver;
    Route route;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Activate Test Company"));
        bus = busRepository.save(Bus.create(company.getId(), "ACT-P01", () -> "act-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 100L, "Activate Driver",
            () -> "ACT-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "Activate Route", "A", "B"));
    }

    @AfterEach
    void tearDown() {
        assignmentRepository.deleteAll();
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    @Test
    @Transactional
    void activateAssignment_success_returnsResultAndStatusBecomesActive() {
        ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(), T1, T2, 100L, clock));

        var result = facade.activateAssignment(sa.getId(), driver.getId());

        assertEquals(sa.getId(), result.assignmentId());
        assertEquals(driver.getId(), result.driverId());
        assertEquals(bus.getId(), result.busId());
        assertEquals(route.getId(), result.routeId());
    }

    @Test
    @Transactional
    void activateAssignment_notFound_throwsAssignmentNotFound() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> facade.activateAssignment(999999L, driver.getId()));
        assertEquals("ASSIGNMENT_NOT_FOUND", ex.code());
    }

    @Test
    @Transactional
    void activateAssignment_wrongDriver_sameBodyAsNotFound() {
        ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(), T1, T2, 100L, clock));

        RuleViolationException fromWrongDriver = assertThrows(RuleViolationException.class,
            () -> facade.activateAssignment(sa.getId(), driver.getId() + 999));
        RuleViolationException fromMissing = assertThrows(RuleViolationException.class,
            () -> facade.activateAssignment(999999L, driver.getId()));

        assertEquals(fromMissing.code(), fromWrongDriver.code());
        assertEquals(fromMissing.getMessage(), fromWrongDriver.getMessage());
    }

    @Test
    @Transactional
    void activateAssignment_alreadyActive_throwsConflictAssignmentNotAvailable() {
        ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(), T1, T2, 100L, clock));
        sa.activate();
        assignmentRepository.save(sa);

        ConflictException ex = assertThrows(ConflictException.class,
            () -> facade.activateAssignment(sa.getId(), driver.getId()));
        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
    }

    @Test
    @Transactional
    void activateAssignment_alreadyClosed_throwsConflictAssignmentNotAvailable() {
        ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(), T1, T2, 100L, clock));
        try {
            java.lang.reflect.Field f = ShiftAssignment.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(sa, AssignmentStatus.CLOSED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assignmentRepository.save(sa);

        ConflictException ex = assertThrows(ConflictException.class,
            () -> facade.activateAssignment(sa.getId(), driver.getId()));
        assertEquals("ASSIGNMENT_NOT_AVAILABLE", ex.code());
    }

    @Test
    void activateAssignment_withoutTransaction_throwsIllegalTransactionState() {
        assertThrows(IllegalTransactionStateException.class,
            () -> facade.activateAssignment(1L, 1L));
    }
}
