package com.dreamteam.safebus.fleet.application;

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
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CreateShiftAssignmentCommandServiceTest {

    private static final Instant T1 = Instant.parse("2025-06-01T08:00:00Z");
    private static final Instant T2 = Instant.parse("2025-06-01T10:00:00Z");
    private static final Instant T3 = Instant.parse("2025-06-01T12:00:00Z");
    private static final Instant T4 = Instant.parse("2025-06-01T14:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(T1, ZoneOffset.UTC);

    @Autowired CreateShiftAssignmentCommandService service;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @MockitoBean CurrentUserProvider currentUserProvider;

    Company company1;
    Company company2;
    Bus bus1;
    Bus bus2;
    Driver driver1;
    Driver driver2;
    Route route1;
    Route route2;

    @BeforeEach
    void setUp() {
        company1 = companyRepository.save(Company.create("Company One"));
        company2 = companyRepository.save(Company.create("Company Two"));

        bus1 = busRepository.save(Bus.create(company1.getId(), "SVC-BUS1", () -> "qr1"));
        bus2 = busRepository.save(Bus.create(company2.getId(), "SVC-BUS2", () -> "qr2"));

        driver1 = driverRepository.save(Driver.create(company1.getId(), 100L, "Driver One",
            () -> "cred1", Duration.ofDays(365), FIXED_CLOCK));
        driver2 = driverRepository.save(Driver.create(company2.getId(), 200L, "Driver Two",
            () -> "cred2", Duration.ofDays(365), FIXED_CLOCK));

        route1 = routeRepository.save(Route.create(company1.getId(), "Route One", "A", "B"));
        route2 = routeRepository.save(Route.create(company2.getId(), "Route Two", "C", "D"));

        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(42L, "SUPERVISOR", company1.getId()));
    }

    @Test
    void create_valid_savedWithAssignedStatus() {
        ShiftAssignment sa = service.create(new CreateShiftAssignmentCommand(
            driver1.getId(), bus1.getId(), route1.getId(), T1, T2));
        assertEquals(AssignmentStatus.ASSIGNED, sa.getStatus());
        assertEquals(42L, sa.getCreatedByUserId());
        assertEquals(driver1.getId(), sa.getDriverId());
        assertEquals(bus1.getId(), sa.getBusId());
    }

    @Test
    void create_invalidPeriod_throwsInvalidPeriod() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus1.getId(), route1.getId(), T2, T1)));
        assertEquals("INVALID_PERIOD", ex.code());
    }

    @Test
    void create_foreignCompanyBus_throwsNotInCompany() {
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus2.getId(), route1.getId(), T1, T2)));
        assertEquals("RESOURCE_NOT_IN_COMPANY", ex.code());
    }

    @Test
    void create_nonExistentBus_sameCodeAndMessageAsForeignBus() {
        RuleViolationException foreign = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus2.getId(), route1.getId(), T1, T2)));

        RuleViolationException nonExistent = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), 999999L, route1.getId(), T1, T2)));

        assertEquals(foreign.code(), nonExistent.code());
        assertEquals(foreign.getMessage(), nonExistent.getMessage());
    }

    @Test
    void create_disabledDriver_throwsResourceDisabled() {
        driver1.disable();
        driverRepository.save(driver1);
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus1.getId(), route1.getId(), T1, T2)));
        assertEquals("RESOURCE_DISABLED", ex.code());
    }

    @Test
    void create_disabledBus_throwsResourceDisabled() {
        bus1.disable();
        busRepository.save(bus1);
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus1.getId(), route1.getId(), T1, T2)));
        assertEquals("RESOURCE_DISABLED", ex.code());
    }

    @Test
    void create_disabledRoute_throwsResourceDisabled() {
        route1.disable();
        routeRepository.save(route1);
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus1.getId(), route1.getId(), T1, T2)));
        assertEquals("RESOURCE_DISABLED", ex.code());
    }

    @Test
    void create_driverOverlap_throwsAssignmentOverlap() {
        service.create(new CreateShiftAssignmentCommand(
            driver1.getId(), bus1.getId(), route1.getId(), T1, T3));

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver1.getId(), bus1.getId(), route1.getId(), T2, T4)));
        assertEquals("ASSIGNMENT_OVERLAP", ex.code());
    }

    @Test
    void create_busOverlap_throwsAssignmentOverlap() {
        // Use a different driver for the second assignment so only the bus overlaps
        Driver driver3 = driverRepository.save(Driver.create(company1.getId(), 300L, "Driver Three",
            () -> "cred3", Duration.ofDays(365), FIXED_CLOCK));

        service.create(new CreateShiftAssignmentCommand(
            driver1.getId(), bus1.getId(), route1.getId(), T1, T3));

        ConflictException ex = assertThrows(ConflictException.class,
            () -> service.create(new CreateShiftAssignmentCommand(
                driver3.getId(), bus1.getId(), route1.getId(), T2, T4)));
        assertEquals("ASSIGNMENT_OVERLAP", ex.code());
    }

    @Test
    void create_contiguousPeriod_savedSuccessfully() {
        service.create(new CreateShiftAssignmentCommand(
            driver1.getId(), bus1.getId(), route1.getId(), T1, T2));

        ShiftAssignment second = service.create(new CreateShiftAssignmentCommand(
            driver1.getId(), bus1.getId(), route1.getId(), T2, T3));
        assertEquals(AssignmentStatus.ASSIGNED, second.getStatus());
    }
}
