package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.fleet.domain.repository.CompanyRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class UpdateBusCapacityTest {

    private static final Instant ORIGINAL_TIME = Instant.parse("2030-01-01T10:00:00.123Z");
    private static final Instant UPDATE_TIME = Instant.parse("2030-01-02T10:00:00.456789123Z");

    @Autowired UpdateBusCapacity service;
    @Autowired BusRepository busRepository;
    @Autowired CompanyRepository companyRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean CurrentUserProvider currentUserProvider;
    @MockitoBean Clock clock;

    private Company company;
    private Company otherCompany;
    private Bus bus;
    private Bus foreignBus;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(Company.create("Capacity Company"));
        otherCompany = companyRepository.save(Company.create("Other Capacity Company"));
        bus = savedBus(company.getId(), "CAP-SVC1", "capacity-service-qr1");
        foreignBus = savedBus(otherCompany.getId(), "CAP-SVC2", "capacity-service-qr2");
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(42L, "SUPERVISOR", company.getId()));
        when(clock.instant()).thenReturn(UPDATE_TIME);
    }

    private Bus savedBus(Long companyId, String plate, String qr) {
        Bus created = Bus.create(companyId, plate, () -> qr);
        created.recordCapacity(30, "TECH-OLD", 7L, Clock.fixed(ORIGINAL_TIME, ZoneOffset.UTC));
        return busRepository.saveAndFlush(created);
    }

    @AfterEach
    void cleanUp() {
        busRepository.deleteById(bus.getId());
        busRepository.deleteById(foreignBus.getId());
        companyRepository.deleteById(company.getId());
        companyRepository.deleteById(otherCompany.getId());
    }

    @Test
    void update_locksBeforeReadingCallerAndStoresCapacityWithCallerAuthorAndClock() {
        when(currentUserProvider.current()).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(TransactionDefinition.ISOLATION_READ_COMMITTED,
                TransactionSynchronizationManager.getCurrentTransactionIsolationLevel());
            Bus locked = entityManager.find(Bus.class, bus.getId());
            assertEquals(LockModeType.PESSIMISTIC_WRITE, entityManager.getLockMode(locked));
            return new AuthenticatedUser(42L, "SUPERVISOR", company.getId());
        });

        Bus result = service.update(new UpdateBusCapacityCommand(bus.getId(), 40, "  TECH-NEW  "));

        assertEquals(bus.getId(), result.getId());
        Bus stored = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(40, stored.getCapacity());
        assertEquals("TECH-NEW", stored.getCapacityReference());
        assertEquals(42L, stored.getCapacityUpdatedByUserId());
        assertEquals(Instant.parse("2030-01-02T10:00:00.456Z"), stored.getCapacityUpdatedAt());
        assertBaseFields(bus, stored);
        assertUnchanged(foreignBus);
    }

    @Test
    void update_authorAlwaysComesFromCurrentCaller() {
        service.update(new UpdateBusCapacityCommand(bus.getId(), 40, "TECH-FIRST"));
        assertEquals(42L, busRepository.findById(bus.getId()).orElseThrow().getCapacityUpdatedByUserId());
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(99L, "SUPERVISOR", company.getId()));

        service.update(new UpdateBusCapacityCommand(bus.getId(), 45, "TECH-SECOND"));

        Bus stored = busRepository.findById(bus.getId()).orElseThrow();
        assertEquals(99L, stored.getCapacityUpdatedByUserId());
        assertEquals(45, stored.getCapacity());
        assertEquals("TECH-SECOND", stored.getCapacityReference());
    }

    @Test
    void update_foreignAndMissingBusHaveSameCodeAndMessageAndPreserveState() {
        long count = busRepository.count();
        ForbiddenOperationException foreign = assertThrows(ForbiddenOperationException.class,
            () -> service.update(new UpdateBusCapacityCommand(foreignBus.getId(), 40, "TECH-NEW")));
        assertEquals("BUS_ACCESS_DENIED", foreign.code());
        assertEquals("bus is not accessible", foreign.getMessage());
        assertEquals(count, busRepository.count());
        assertUnchanged(bus);
        assertUnchanged(foreignBus);

        ForbiddenOperationException missing = assertThrows(ForbiddenOperationException.class,
            () -> service.update(new UpdateBusCapacityCommand(Long.MAX_VALUE, 40, "TECH-NEW")));
        assertEquals(foreign.code(), missing.code());
        assertEquals(foreign.getMessage(), missing.getMessage());
        assertEquals(count, busRepository.count());
        assertUnchanged(bus);
        assertUnchanged(foreignBus);
    }

    static Stream<Arguments> invalidUpdates() {
        return Stream.of(
            Arguments.of(40, null, "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(40, "A".repeat(101), "CAPACITY_REFERENCE_TOO_LONG"),
            Arguments.of(null, "TECH-NEW", "CAPACITY_REQUIRED"),
            Arguments.of(12.5, "TECH-NEW", "INVALID_CAPACITY"),
            Arguments.of(0, "TECH-NEW", "INVALID_CAPACITY"),
            Arguments.of(-1, "TECH-NEW", "INVALID_CAPACITY"),
            Arguments.of((long) Integer.MAX_VALUE + 1, "TECH-NEW", "INVALID_CAPACITY")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void update_invalidValuePreservesStoredCapacityReferenceAuthorAndTime(Number capacity, String reference, String code) {
        long count = busRepository.count();
        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> service.update(new UpdateBusCapacityCommand(bus.getId(), capacity, reference)));

        assertEquals(code, ex.code());
        assertEquals(count, busRepository.count());
        assertUnchanged(bus);
        assertUnchanged(foreignBus);
    }

    private void assertUnchanged(Bus before) {
        Bus after = busRepository.findById(before.getId()).orElseThrow();
        assertBaseFields(before, after);
        assertEquals(before.getCapacity(), after.getCapacity());
        assertEquals(before.getCapacityReference(), after.getCapacityReference());
        assertEquals(before.getCapacityUpdatedByUserId(), after.getCapacityUpdatedByUserId());
        assertEquals(before.getCapacityUpdatedAt(), after.getCapacityUpdatedAt());
    }

    private void assertBaseFields(Bus before, Bus after) {
        assertEquals(before.getId(), after.getId());
        assertEquals(before.getCompanyId(), after.getCompanyId());
        assertEquals(before.getPlate(), after.getPlate());
        assertEquals(before.getQrCode(), after.getQrCode());
        assertEquals(before.isEnabled(), after.isEnabled());
    }
}
