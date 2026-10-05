package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadePassengerTest {

    @Autowired FleetContextFacade facade;
    @Autowired CompanyRepository companyRepository;
    @Autowired BusRepository busRepository;
    @Autowired DriverRepository driverRepository;
    @Autowired RouteRepository routeRepository;
    @Autowired Clock clock;

    Company company;
    Bus bus;
    Driver driver;
    Route route;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("FCP Test Co"));
        bus     = busRepository.save(Bus.create(company.getId(), "FCP-001", () -> "FCP-QR-001"));
        driver  = driverRepository.save(Driver.create(company.getId(), 9001L, "FCP Driver",
                      () -> "FCP-DRV-QR", Duration.ofDays(365), clock));
        route   = routeRepository.save(Route.create(company.getId(), "FCP Route", "A", "B"));
    }

    @AfterEach
    void tearDown() {
        driverRepository.deleteAll();
        busRepository.delete(bus);
        routeRepository.delete(route);
        companyRepository.delete(company);
    }

    // --- findBusByQrCode ---

    @Test
    void findBusByQrCode_found_returnsBusInfo() {
        Optional<FleetContextFacade.BusInfo> result = facade.findBusByQrCode("FCP-QR-001");
        assertTrue(result.isPresent());
        assertEquals(bus.getId(), result.get().busId());
        assertEquals(company.getId(), result.get().companyId());
        assertTrue(result.get().enabled());
    }

    @Test
    void findBusByQrCode_unknownCode_returnsEmpty() {
        assertTrue(facade.findBusByQrCode("UNKNOWN-QR").isEmpty());
    }

    @Test
    void findBusByQrCode_disabledBus_returnsInfoWithEnabledFalse() {
        bus.disable();
        busRepository.save(bus);
        Optional<FleetContextFacade.BusInfo> result = facade.findBusByQrCode("FCP-QR-001");
        assertTrue(result.isPresent());
        assertFalse(result.get().enabled());
    }

    // --- describeService ---

    @Test
    void describeService_validIds_returnsServiceInfo() {
        Optional<FleetContextFacade.ServiceInfo> result =
            facade.describeService(bus.getId(), route.getId(), driver.getId());
        assertTrue(result.isPresent());
        FleetContextFacade.ServiceInfo info = result.get();
        assertEquals("FCP-001", info.plate());
        assertEquals("FCP Test Co", info.companyName());
        assertTrue(info.companyValidated());
        assertEquals("FCP Route", info.routeName());
        assertEquals("A", info.origin());
        assertEquals("B", info.destination());
        assertEquals("FCP Driver", info.driverPublicName());
    }

    @Test
    void describeService_unknownBus_returnsEmpty() {
        assertTrue(facade.describeService(999999L, route.getId(), driver.getId()).isEmpty());
    }

    @Test
    void describeService_unknownRoute_returnsEmpty() {
        assertTrue(facade.describeService(bus.getId(), 999999L, driver.getId()).isEmpty());
    }

    @Test
    void describeService_unknownDriver_returnsEmpty() {
        assertTrue(facade.describeService(bus.getId(), route.getId(), 999999L).isEmpty());
    }
}
