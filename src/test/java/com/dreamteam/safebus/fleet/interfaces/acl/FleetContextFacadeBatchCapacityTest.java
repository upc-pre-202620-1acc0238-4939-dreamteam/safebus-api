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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadeBatchCapacityTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Autowired FleetContextFacade facade;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;

    private Company company;
    private Bus withCapacity;
    private Bus withoutCapacity;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Batch Capacity Company"));
        withCapacity = busRepository.save(Bus.create(company.getId(), "BC-001", () -> "bc-qr-1"));
        withCapacity.recordCapacity(42, "TECH-BC", 1L, CLOCK);
        withCapacity = busRepository.save(withCapacity);
        withoutCapacity = busRepository.save(Bus.create(company.getId(), "BC-002", () -> "bc-qr-2"));
    }

    @AfterEach
    void cleanUp() {
        busRepository.deleteAll(List.of(withCapacity, withoutCapacity));
        companyRepository.delete(company);
    }

    @Test
    void returnsOnlyBusesWithARecordedCapacity() {
        Map<Long, Integer> result = facade.findCapacitiesByBusIds(
            List.of(withCapacity.getId(), withoutCapacity.getId(), 987_654_321L));

        assertEquals(Map.of(withCapacity.getId(), 42), result);
    }

    @Test
    void emptyOrNullInputGivesAnEmptyMap() {
        assertTrue(facade.findCapacitiesByBusIds(List.of()).isEmpty());
        assertTrue(facade.findCapacitiesByBusIds(null).isEmpty());
    }
}
