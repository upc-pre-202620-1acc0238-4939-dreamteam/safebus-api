package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.model.Driver;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.fleet.domain.repository.DriverRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FleetContextFacadeLocationTest {

    @Autowired FleetContextFacade facade;
    @Autowired DriverRepository driverRepository;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired Clock clock;

    @Test
    void findDriverByUserAccountId_found_returnsDriverInfo() {
        Driver saved = driverRepository.save(
            Driver.create(20L, 200L, "Location Driver",
                () -> "LOC-DRV-QR-001", Duration.ofDays(365), clock));

        var result = facade.findDriverByUserAccountId(200L);

        assertTrue(result.isPresent());
        FleetContextFacade.DriverInfo info = result.get();
        assertEquals(saved.getId(), info.driverId());
        assertEquals(200L, info.userAccountId());
        assertEquals(20L, info.companyId());
        assertTrue(info.enabled());
    }

    @Test
    void findDriverByUserAccountId_notFound_returnsEmpty() {
        assertFalse(facade.findDriverByUserAccountId(999999L).isPresent());
    }

    @Test
    void findBusCompanyId_found_returnsCompanyId() {
        Company company = companyRepository.save(Company.create("Facade Bus Company"));
        Bus bus = busRepository.save(Bus.create(company.getId(), "FAC-B01", () -> "fac-bus-qr1"));

        var result = facade.findBusCompanyId(bus.getId());

        assertTrue(result.isPresent());
        assertEquals(company.getId(), result.get());
    }

    @Test
    void findBusCompanyId_notFound_returnsEmpty() {
        assertFalse(facade.findBusCompanyId(999999L).isPresent());
    }
}
