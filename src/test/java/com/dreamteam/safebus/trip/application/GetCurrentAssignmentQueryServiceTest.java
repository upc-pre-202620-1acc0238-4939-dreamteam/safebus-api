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
import com.dreamteam.safebus.shared.domain.exceptions.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GetCurrentAssignmentQueryServiceTest {

    @Autowired GetCurrentAssignmentQueryService queryService;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired ShiftAssignmentRepository assignmentRepository;
    @Autowired Clock clock;
    @MockitoBean CurrentUserProvider currentUserProvider;

    @Test
    void getCurrentAssignment_hasAssignment_returnsAllSixFields() {
        Company c = companyRepository.save(Company.create("GCA Company"));
        Bus b = busRepository.save(Bus.create(c.getId(), "GCA-P01", () -> "gca-qr1"));
        Driver d = driverRepository.save(Driver.create(c.getId(), 400L, "GCA Driver",
            () -> "gca-drv-qr1", Duration.ofDays(365), clock));
        Route r = routeRepository.save(
            Route.create(c.getId(), "GCA Route", "Origin City", "Destination City"));

        Instant start = Instant.now(clock).plusSeconds(3600);
        Instant end = Instant.now(clock).plusSeconds(7200);
        ShiftAssignment sa = assignmentRepository.save(
            ShiftAssignment.create(d.getId(), b.getId(), r.getId(), start, end, 1L, clock));

        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(400L, "DRIVER", c.getId()));

        CurrentAssignmentResult result = queryService.getCurrentAssignment();

        assertEquals(sa.getId(), result.assignmentId());
        assertEquals("ASSIGNED", result.status());
        assertEquals("GCA-P01", result.busPlate());
        assertEquals("GCA Route", result.routeName());
        assertEquals("Origin City", result.origin());
        assertEquals("Destination City", result.destination());
        assertEquals(start, result.plannedStart());
        assertEquals(end, result.plannedEnd());
    }

    @Test
    void getCurrentAssignment_noAssignment_throwsAssignmentNotFound() {
        Company c = companyRepository.save(Company.create("GCA Company 2"));
        driverRepository.save(Driver.create(c.getId(), 401L, "GCA Driver 2",
            () -> "gca-drv-qr2", Duration.ofDays(365), clock));

        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(401L, "DRIVER", c.getId()));

        NotFoundException ex = assertThrows(NotFoundException.class,
            () -> queryService.getCurrentAssignment());
        assertEquals("ASSIGNMENT_NOT_FOUND", ex.code());
    }

    @Test
    void getCurrentAssignment_anotherDriverHasAssignment_throwsNotFound() {
        Company c = companyRepository.save(Company.create("GCA Company 3"));
        Bus b = busRepository.save(Bus.create(c.getId(), "GCA-P02", () -> "gca-qr3"));
        Driver d1 = driverRepository.save(Driver.create(c.getId(), 402L, "GCA Driver 3",
            () -> "gca-drv-qr3", Duration.ofDays(365), clock));
        Driver d2 = driverRepository.save(Driver.create(c.getId(), 403L, "GCA Driver 4",
            () -> "gca-drv-qr4", Duration.ofDays(365), clock));
        Route r = routeRepository.save(Route.create(c.getId(), "GCA Route 2", "A", "B"));

        Instant start = Instant.now(clock).plusSeconds(3600);
        Instant end = Instant.now(clock).plusSeconds(7200);
        assignmentRepository.save(
            ShiftAssignment.create(d2.getId(), b.getId(), r.getId(), start, end, 1L, clock));

        when(currentUserProvider.current())
            .thenReturn(new AuthenticatedUser(402L, "DRIVER", c.getId()));

        NotFoundException ex = assertThrows(NotFoundException.class,
            () -> queryService.getCurrentAssignment());
        assertEquals("ASSIGNMENT_NOT_FOUND", ex.code());
    }
}
