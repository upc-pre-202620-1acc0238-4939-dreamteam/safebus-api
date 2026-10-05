package com.dreamteam.safebus.fleet.domain.model;

import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BusCapacityTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-01-01T10:00:00.123456789Z"), ZoneOffset.UTC);
    private static final Instant STORED_TIME = Instant.parse("2030-01-01T10:00:00.123Z");

    private Bus bus() {
        return Bus.create(1L, "CAP-001", () -> "capacity-qr");
    }

    @Test
    void newBus_hasNoRecordedCapacity() {
        Bus bus = bus();
        assertNull(bus.getCapacity());
        assertNull(bus.getCapacityReference());
        assertNull(bus.getCapacityUpdatedByUserId());
        assertNull(bus.getCapacityUpdatedAt());
    }

    @Test
    void recordCapacity_trimsReferenceAndStoresAuthorAndClockTimeToMilliseconds() {
        Bus bus = bus();
        bus.recordCapacity(40, "  TECH-001  ", 42L, CLOCK);

        assertEquals(40, bus.getCapacity());
        assertEquals("TECH-001", bus.getCapacityReference());
        assertEquals(42L, bus.getCapacityUpdatedByUserId());
        assertEquals(STORED_TIME, bus.getCapacityUpdatedAt());
        assertOriginalBusFields(bus);
    }

    static Stream<Arguments> validCapacities() {
        return Stream.of(
            Arguments.of(1, 1),
            Arguments.of(Integer.MAX_VALUE, Integer.MAX_VALUE),
            Arguments.of(12.0, 12),
            Arguments.of(12.0f, 12),
            Arguments.of(12L, 12),
            Arguments.of(new BigDecimal("12.0"), 12),
            Arguments.of(new BigDecimal("12.0000000000000000000"), 12),
            Arguments.of(new BigInteger("12"), 12)
        );
    }

    @ParameterizedTest
    @MethodSource("validCapacities")
    void recordCapacity_acceptsPositiveExactIntegers(Number capacity, int expected) {
        Bus bus = bus();
        bus.recordCapacity(capacity, "TECH-001", 42L, CLOCK);
        assertEquals(expected, bus.getCapacity());
    }

    @Test
    void recordCapacity_acceptsReferenceOf100CharactersAfterTrimming() {
        Bus bus = bus();
        bus.recordCapacity(40, " " + "A".repeat(100) + " ", 42L, CLOCK);
        assertEquals("A".repeat(100), bus.getCapacityReference());
    }

    @Test
    void recordCapacity_secondUpdateReplacesCapacityReferenceAuthorAndTime() {
        Bus bus = bus();
        bus.recordCapacity(40, "TECH-001", 42L, CLOCK);
        Clock later = Clock.offset(CLOCK, java.time.Duration.ofMinutes(1));

        bus.recordCapacity(50, "TECH-002", 43L, later);

        assertEquals(50, bus.getCapacity());
        assertEquals("TECH-002", bus.getCapacityReference());
        assertEquals(43L, bus.getCapacityUpdatedByUserId());
        assertEquals(STORED_TIME.plusSeconds(60), bus.getCapacityUpdatedAt());
        assertOriginalBusFields(bus);
    }

    static Stream<Arguments> invalidUpdates() {
        return Stream.of(
            Arguments.of(40, null, "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(40, "", "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(40, " \t\n ", "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(null, null, "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(12.5, " ", "CAPACITY_REFERENCE_REQUIRED"),
            Arguments.of(40, "A".repeat(101), "CAPACITY_REFERENCE_TOO_LONG"),
            Arguments.of(null, "A".repeat(101), "CAPACITY_REFERENCE_TOO_LONG"),
            Arguments.of(12.5, "A".repeat(101), "CAPACITY_REFERENCE_TOO_LONG"),
            Arguments.of(null, "TECH-002", "CAPACITY_REQUIRED"),
            Arguments.of(12.5, "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(new BigDecimal("12.0000000000000000001"), "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(0, "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(-1, "TECH-002", "INVALID_CAPACITY"),
            Arguments.of((long) Integer.MAX_VALUE + 1, "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(new BigInteger("99999999999999999999"), "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(Double.NaN, "TECH-002", "INVALID_CAPACITY"),
            Arguments.of(Double.POSITIVE_INFINITY, "TECH-002", "INVALID_CAPACITY")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidUpdates")
    void recordCapacity_rejectsInSpecifiedOrderAndPreservesAllState(Number capacity, String reference, String code) {
        Bus bus = bus();
        bus.recordCapacity(40, "TECH-001", 42L, CLOCK);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> bus.recordCapacity(capacity, reference, 99L, Clock.offset(CLOCK, java.time.Duration.ofDays(1))));

        assertEquals(code, ex.code());
        assertEquals(40, bus.getCapacity());
        assertEquals("TECH-001", bus.getCapacityReference());
        assertEquals(42L, bus.getCapacityUpdatedByUserId());
        assertEquals(STORED_TIME, bus.getCapacityUpdatedAt());
        assertOriginalBusFields(bus);
    }

    private void assertOriginalBusFields(Bus bus) {
        assertNull(bus.getId());
        assertEquals(1L, bus.getCompanyId());
        assertEquals("CAP-001", bus.getPlate());
        assertEquals("capacity-qr", bus.getQrCode());
        assertTrue(bus.isEnabled());
    }
}
