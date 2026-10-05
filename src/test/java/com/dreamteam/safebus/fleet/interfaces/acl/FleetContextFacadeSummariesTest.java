package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadeSummariesTest {

    @Autowired FleetContextFacade facade;
    @Autowired CompanyRepository companyRepository;
    @Autowired Clock clock;
    @MockitoSpyBean DriverRepository driverRepository;
    @MockitoSpyBean RouteRepository routeRepository;

    private Company company;
    private Driver driverOne;
    private Driver driverTwo;
    private Route routeOne;
    private Route routeTwo;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Summaries Company"));
        driverOne = driverRepository.save(Driver.create(company.getId(), 9701L, "  Ana Summary ",
            () -> "SUM-DRV-QR-1", Duration.ofDays(365), clock));
        driverTwo = driverRepository.save(Driver.create(company.getId(), 9702L, "Beto Summary",
            () -> "SUM-DRV-QR-2", Duration.ofDays(365), clock));
        routeOne = routeRepository.save(Route.create(company.getId(), "Route One", "A", "B"));
        routeTwo = routeRepository.save(Route.create(company.getId(), "Route Two", "C", "D"));
    }

    @AfterEach
    void cleanUp() {
        driverRepository.deleteAll(List.of(driverOne, driverTwo));
        routeRepository.deleteAll(List.of(routeOne, routeTwo));
        companyRepository.deleteById(company.getId());
    }

    @Test
    void findDriversByIds_returnsTheRequestedDriversWithTheirFullName() {
        Map<Long, FleetContextFacade.DriverSummary> result =
            facade.findDriversByIds(List.of(driverOne.getId(), driverTwo.getId()));

        assertEquals(Map.of(
            driverOne.getId(), new FleetContextFacade.DriverSummary(driverOne.getId(), "Ana Summary"),
            driverTwo.getId(), new FleetContextFacade.DriverSummary(driverTwo.getId(), "Beto Summary")),
            result);
    }

    @Test
    void findDriversByIds_unknownIdsAreAbsentAndOthersAreNotReturned() {
        Map<Long, FleetContextFacade.DriverSummary> result =
            facade.findDriversByIds(Set.of(driverOne.getId(), Long.MAX_VALUE));

        assertEquals(Set.of(driverOne.getId()), result.keySet());
    }

    @Test
    void findDriversByIds_emptyOrNullCollectionReturnsEmptyMapWithoutQuerying() {
        assertTrue(facade.findDriversByIds(List.of()).isEmpty());
        assertTrue(facade.findDriversByIds(null).isEmpty());

        verify(driverRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void findDriversByIds_summaryExposesOnlyIdAndFullName() {
        List<String> components = Arrays.stream(FleetContextFacade.DriverSummary.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertEquals(List.of("driverId", "fullName"), components);
    }

    @Test
    void findDriversByIds_resultIsImmutable() {
        Map<Long, FleetContextFacade.DriverSummary> result = facade.findDriversByIds(List.of(driverOne.getId()));

        assertThrows(UnsupportedOperationException.class, result::clear);
    }

    @Test
    void findRoutesByIds_returnsTheRequestedRoutes() {
        Map<Long, FleetContextFacade.RouteSummary> result =
            facade.findRoutesByIds(List.of(routeOne.getId(), routeTwo.getId()));

        assertEquals(Map.of(
            routeOne.getId(), new FleetContextFacade.RouteSummary(routeOne.getId(), "Route One", "A", "B"),
            routeTwo.getId(), new FleetContextFacade.RouteSummary(routeTwo.getId(), "Route Two", "C", "D")),
            result);
    }

    @Test
    void findRoutesByIds_unknownIdsAreAbsentAndOthersAreNotReturned() {
        Map<Long, FleetContextFacade.RouteSummary> result =
            facade.findRoutesByIds(Set.of(routeTwo.getId(), Long.MAX_VALUE));

        assertEquals(Set.of(routeTwo.getId()), result.keySet());
    }

    @Test
    void findRoutesByIds_emptyOrNullCollectionReturnsEmptyMapWithoutQuerying() {
        assertTrue(facade.findRoutesByIds(List.of()).isEmpty());
        assertTrue(facade.findRoutesByIds(null).isEmpty());

        verify(routeRepository, never()).findAllById(org.mockito.ArgumentMatchers.any());
    }
}
