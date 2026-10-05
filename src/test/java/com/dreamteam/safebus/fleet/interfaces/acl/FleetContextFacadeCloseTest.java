package com.dreamteam.safebus.fleet.interfaces.acl;

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
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
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
class FleetContextFacadeCloseTest {

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
        company = companyRepository.save(Company.create("Close Facade Company"));
        bus = busRepository.save(Bus.create(company.getId(), "CLS-P01", () -> "cls-bus-qr1"));
        driver = driverRepository.save(Driver.create(company.getId(), 110L, "Close Driver",
            () -> "CLS-DRV-QR-001", Duration.ofDays(365), clock));
        route = routeRepository.save(Route.create(company.getId(), "Close Route", "A", "B"));
    }

    @AfterEach
    void tearDown() {
        assignmentRepository.deleteAll();
        driverRepository.delete(driver);
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    private ShiftAssignment assignment(boolean active) {
        ShiftAssignment sa = assignmentRepository.save(ShiftAssignment.create(
            driver.getId(), bus.getId(), route.getId(), T1, T2, 100L, clock));
        if (active) {
            sa.activate();
            sa = assignmentRepository.save(sa);
        }
        return sa;
    }

    @Test
    @Transactional
    void closeAssignment_active_becomesClosed() {
        ShiftAssignment sa = assignment(true);

        facade.closeAssignment(sa.getId());

        assertEquals(AssignmentStatus.CLOSED,
            assignmentRepository.findById(sa.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void closeAssignment_alreadyClosed_isIdempotent() {
        ShiftAssignment sa = assignment(true);
        facade.closeAssignment(sa.getId());

        facade.closeAssignment(sa.getId());

        assertEquals(AssignmentStatus.CLOSED,
            assignmentRepository.findById(sa.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void closeAssignment_assigned_throwsConflictAndKeepsStatus() {
        ShiftAssignment sa = assignment(false);

        ConflictException ex = assertThrows(ConflictException.class,
            () -> facade.closeAssignment(sa.getId()));

        assertEquals("ASSIGNMENT_NOT_ACTIVE", ex.code());
        assertEquals(AssignmentStatus.ASSIGNED,
            assignmentRepository.findById(sa.getId()).orElseThrow().getStatus());
    }

    @Test
    @Transactional
    void closeAssignment_missing_throwsNotFound() {
        NotFoundException ex = assertThrows(NotFoundException.class,
            () -> facade.closeAssignment(999999L));

        assertEquals("ASSIGNMENT_NOT_FOUND", ex.code());
    }

    @Test
    void closeAssignment_withoutTransaction_throwsIllegalTransactionState() {
        assertThrows(IllegalTransactionStateException.class, () -> facade.closeAssignment(1L));
    }
}
