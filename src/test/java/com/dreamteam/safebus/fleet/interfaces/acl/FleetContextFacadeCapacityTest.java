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

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class FleetContextFacadeCapacityTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);

    @Autowired FleetContextFacade facade;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;

    private Company company;
    private Bus bus;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Capacity Facade Company"));
        bus = busRepository.saveAndFlush(Bus.create(company.getId(), "CAP-FAC1", () -> "capacity-facade-qr"));
    }

    @AfterEach
    void cleanUp() {
        busRepository.deleteById(bus.getId());
        companyRepository.deleteById(company.getId());
    }

    @Test
    void findBusCapacity_foundWithCapacityReturnsAllRecordFields() {
        bus.recordCapacity(40, "  TECH-001  ", 42L, CLOCK);
        busRepository.saveAndFlush(bus);

        FleetContextFacade.BusCapacityInfo info = facade.findBusCapacity(bus.getId()).orElseThrow();

        assertEquals(new FleetContextFacade.BusCapacityInfo(bus.getId(), company.getId(), 40,
            "TECH-001", Instant.parse("2030-01-01T10:00:00.123Z")), info);
        assertTrue(info.getClass().isRecord());
    }

    @Test
    void findBusCapacity_foundWithoutRecordedCapacityReturnsNullCapacity() {
        FleetContextFacade.BusCapacityInfo info = facade.findBusCapacity(bus.getId()).orElseThrow();

        assertEquals(bus.getId(), info.busId());
        assertEquals(company.getId(), info.companyId());
        assertNull(info.capacity());
        assertNull(info.reference());
        assertNull(info.updatedAt());
        assertNull(busRepository.findById(bus.getId()).orElseThrow().getCapacity());
    }

    @Test
    void findBusCapacity_missingBusReturnsEmpty() {
        long count = busRepository.count();
        assertTrue(facade.findBusCapacity(Long.MAX_VALUE).isEmpty());
        assertEquals(count, busRepository.count());
        assertNull(busRepository.findById(bus.getId()).orElseThrow().getCapacity());
    }

    @Test
    void findBusCapacity_subsequentQuerySeesLatestCapacityAndPreviousRecordStaysImmutable() {
        bus.recordCapacity(40, "TECH-001", 42L, CLOCK);
        busRepository.saveAndFlush(bus);
        FleetContextFacade.BusCapacityInfo first = facade.findBusCapacity(bus.getId()).orElseThrow();
        bus.recordCapacity(50, "TECH-002", 43L, Clock.offset(CLOCK, java.time.Duration.ofMinutes(1)));
        busRepository.saveAndFlush(bus);

        FleetContextFacade.BusCapacityInfo latest = facade.findBusCapacity(bus.getId()).orElseThrow();

        assertEquals(50, latest.capacity());
        assertEquals("TECH-002", latest.reference());
        assertEquals(Instant.parse("2030-01-01T10:01:00.123Z"), latest.updatedAt());
        assertEquals(40, first.capacity());
        assertEquals("TECH-001", first.reference());
        assertEquals(Instant.parse("2030-01-01T10:00:00.123Z"), first.updatedAt());
    }
}
