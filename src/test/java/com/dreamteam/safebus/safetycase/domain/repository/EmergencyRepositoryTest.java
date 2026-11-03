package com.dreamteam.safebus.safetycase.domain.repository;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class EmergencyRepositoryTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2030-05-01T10:00:00Z"), ZoneOffset.UTC);
    private static final long GROUP = 81001L;

    @Autowired EmergencyRepository repository;
    @Autowired JdbcTemplate jdbcTemplate;

    private final List<String> created = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        repository.deleteAllById(created);
    }

    private Emergency fromGroup(long groupId) {
        Emergency e = Emergency.activateByPassengerGroup(UUID.randomUUID().toString(),
            1L, 2L, 3L, 4L, groupId, CLOCK);
        created.add(e.getId());
        return e;
    }

    private Emergency byDriver() {
        Emergency e = Emergency.activateByDriver(UUID.randomUUID().toString(), 1L, 5L, 2L, 3L, 4L,
            new GeoPoint(-12.0, -77.0), CLOCK.instant(), CLOCK);
        created.add(e.getId());
        return e;
    }

    @Test
    void twoEmergenciesForOneGroupViolateTheUniqueConstraint() {
        repository.saveAndFlush(fromGroup(GROUP));

        assertThrows(DataIntegrityViolationException.class, () -> repository.saveAndFlush(fromGroup(GROUP)));
        assertEquals(1, created.stream().filter(repository::existsById).count());
    }

    @Test
    void differentGroupsCanEachHaveTheirOwnEmergency() {
        repository.saveAndFlush(fromGroup(GROUP));
        repository.saveAndFlush(fromGroup(GROUP + 1));

        assertTrue(repository.findByGroupId(GROUP).isPresent());
        assertTrue(repository.findByGroupId(GROUP + 1).isPresent());
    }

    @Test
    void driverEmergenciesWithoutAGroupNeverCollide() {
        repository.saveAndFlush(byDriver());
        repository.saveAndFlush(byDriver());
        repository.saveAndFlush(byDriver());

        assertEquals(3, created.stream().filter(repository::existsById).count());
    }

    @Test
    void findByGroupId_returnsTheLinkedEmergencyOnly() {
        Emergency stored = repository.saveAndFlush(fromGroup(GROUP));
        repository.saveAndFlush(byDriver());

        assertEquals(stored.getId(), repository.findByGroupId(GROUP).orElseThrow().getId());
        assertTrue(repository.findByGroupId(GROUP + 99).isEmpty());
    }

    // ddl-auto=update never relaxes a NOT NULL on an existing database, so these must be nullable from the start
    @Test
    void columnsLeftEmptyByAGroupEmergencyAreNullableInTheSchema() {
        Map<String, String> nullable = jdbcTemplate.queryForList(
                "select upper(column_name) as name, is_nullable as nullable from information_schema.columns "
                    + "where upper(table_name) = 'EMERGENCY'").stream()
            .collect(Collectors.toMap(r -> (String) r.get("name"), r -> (String) r.get("nullable")));

        for (String column : List.of("DRIVER_ID", "GROUP_ID", "LATITUDE", "LONGITUDE", "OCCUPANCY_COUNT",
                "RESPONSIBLE_SUPERVISOR_USER_ID", "ATTENTION_STARTED_AT", "CLOSED_AT", "OUTCOME", "USER_RESPONSE")) {
            assertEquals("YES", nullable.get(column), column + " must be nullable");
        }
    }
}
