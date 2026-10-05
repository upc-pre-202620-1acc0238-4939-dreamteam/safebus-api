package com.dreamteam.safebus.fleet.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadeBusListTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Autowired FleetContextFacade facade;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;

    private Company company;
    private Company otherCompany;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Bus List Company"));
        otherCompany = companyRepository.save(Company.create("Other Bus List Company"));
    }

    @AfterEach
    void cleanUp() {
        busRepository.deleteAll(busRepository.findByCompanyIdOrderByPlateAsc(company.getId()));
        busRepository.deleteAll(busRepository.findByCompanyIdOrderByPlateAsc(otherCompany.getId()));
        companyRepository.deleteById(company.getId());
        companyRepository.deleteById(otherCompany.getId());
    }

    private Bus saveBus(Company owner, String plate) {
        return busRepository.save(Bus.create(owner.getId(), plate, () -> "bus-list-qr-" + plate));
    }

    @Test
    void listBusesOfCompany_returnsOnlyThatCompanysBuses() {
        Bus mine = saveBus(company, "BL-001");
        saveBus(otherCompany, "BL-002");

        List<FleetContextFacade.BusSummary> result = facade.listBusesOfCompany(company.getId());

        assertEquals(1, result.size());
        assertEquals(mine.getId(), result.get(0).busId());
        assertEquals("BL-001", result.get(0).plate());
    }

    @Test
    void listBusesOfCompany_includesDisabledBusesWithTheEnabledFlag() {
        Bus enabled = saveBus(company, "BL-010");
        Bus disabled = saveBus(company, "BL-011");
        disabled.disable();
        busRepository.save(disabled);

        List<FleetContextFacade.BusSummary> result = facade.listBusesOfCompany(company.getId());

        assertEquals(List.of(
            new FleetContextFacade.BusSummary(enabled.getId(), "BL-010", true, null),
            new FleetContextFacade.BusSummary(disabled.getId(), "BL-011", false, null)), result);
    }

    @Test
    void listBusesOfCompany_ordersByPlate() {
        saveBus(company, "BL-030");
        saveBus(company, "BL-010");
        saveBus(company, "BL-020");

        List<String> plates = facade.listBusesOfCompany(company.getId()).stream()
            .map(FleetContextFacade.BusSummary::plate).toList();

        assertEquals(List.of("BL-010", "BL-020", "BL-030"), plates);
    }

    @Test
    void listBusesOfCompany_capacityIsPresentWhenRecordedAndNullOtherwise() {
        Bus withCapacity = saveBus(company, "BL-040");
        withCapacity.recordCapacity(45, "TECH-040", 7L, CLOCK);
        busRepository.save(withCapacity);
        saveBus(company, "BL-041");

        List<FleetContextFacade.BusSummary> result = facade.listBusesOfCompany(company.getId());

        assertEquals(45, result.get(0).capacity());
        assertNull(result.get(1).capacity());
    }

    @Test
    void listBusesOfCompany_companyWithoutBusesReturnsEmptyList() {
        assertTrue(facade.listBusesOfCompany(company.getId()).isEmpty());
    }
}
