package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.repository.BusRepository;
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerJourneyRepository;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.when;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GetBusSafetyHistoryQueryServiceTest {
    @Autowired GetBusSafetyHistoryQueryService service;
    @MockitoBean CurrentUserProvider currentUserProvider;

    private static final Long USER_ID = 91001L;
    private static final Long SHIFT_ID = 92001L;
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00.123Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String OUTCOME = "private incident outcome";
    private static final String USER_RESPONSE = "private passenger response";

    @Autowired EmergencyRepository emergencyRepository;
    @Autowired PassengerJourneyRepository journeyRepository;
    @Autowired BusRepository busRepository;
    @Autowired ObjectMapper objectMapper;
    private final List<String> emergencyIds = new ArrayList<>();
    private final List<Long> journeyIds = new ArrayList<>();
    private final List<Long> busIds = new ArrayList<>();
    private Long busId;
    private Long otherBusId;

    private void createBuses() {
        busId = saveBus("HIS-001").getId();
        otherBusId = saveBus("HIS-002").getId();
    }

    private Bus saveBus(String plate) {
        Bus bus = busRepository.saveAndFlush(Bus.create(93001L, plate, () -> UUID.randomUUID().toString()));
        busIds.add(bus.getId());
        return bus;
    }

    @AfterEach
    void cleanUp() {
        emergencyRepository.deleteAllById(emergencyIds);
        journeyRepository.deleteAllById(journeyIds);
        busRepository.deleteAllById(busIds);
    }

    private PassengerJourney journey(Long userId, Long journeyBusId, boolean ended) {
        PassengerJourney journey = PassengerJourney.start(userId, journeyBusId, SHIFT_ID, CLOCK);
        if (ended) journey.end(JourneyEndReason.MANUAL, NOW.plusSeconds(60));
        var saved = journeyRepository.saveAndFlush(journey);
        journeyIds.add(saved.getId());
        return saved;
    }

    private Emergency emergency(Long emergencyBusId, Long shiftId, int seconds, EmergencyStatus state) {
        Instant receivedAt = NOW.plusSeconds(seconds);
        Emergency emergency = Emergency.activateByDriver(UUID.randomUUID().toString(),
            93001L, 94001L, emergencyBusId, shiftId, 95001L, new GeoPoint(-12.046, -77.042),
            NOW.minusSeconds(seconds), Clock.fixed(receivedAt, ZoneOffset.UTC));
        if (state != EmergencyStatus.ACTIVE) emergency.startAttention(96001L, receivedAt.plusSeconds(1));
        if (state == EmergencyStatus.CLOSED) emergency.close(OUTCOME, USER_RESPONSE, receivedAt.plusSeconds(2));
        var saved = emergencyRepository.saveAndFlush(emergency);
        emergencyIds.add(saved.getId());
        return saved;
    }

    private List<Emergency> seedHistory() {
        Emergency active = emergency(busId, SHIFT_ID, 10, EmergencyStatus.ACTIVE);
        Emergency inProgress = emergency(busId, SHIFT_ID, 20, EmergencyStatus.IN_PROGRESS);
        Emergency closed = emergency(busId, SHIFT_ID, 30, EmergencyStatus.CLOSED);
        emergency(otherBusId, SHIFT_ID, 40, EmergencyStatus.ACTIVE);
        emergency(busId, SHIFT_ID + 1, 50, EmergencyStatus.ACTIVE);
        return List.of(closed, inProgress, active);
    }

    private Snapshot snapshot() {
        return new Snapshot(emergencyRepository.findAll(), journeyRepository.findAll(), busRepository.findAll());
    }

    private void assertUnchanged(Snapshot before) {
        assertThat(snapshot()).usingRecursiveComparison().ignoringCollectionOrder().isEqualTo(before);
    }

    private record Snapshot(List<Emergency> emergencies, List<PassengerJourney> journeys, List<Bus> buses) {}

    @BeforeEach
    void setUp() {
        createBuses();
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(USER_ID, "PASSENGER", null));
    }

    @Test
    void getForBus_returnsOnlyActiveJourneyBusAndShiftNewestFirstWithPublicFields() {
        journey(USER_ID, busId, false);
        List<Emergency> expected = seedHistory();
        Snapshot before = snapshot();

        var result = service.getForBus(busId);

        assertEquals(busId, result.busId());
        assertEquals(SHIFT_ID, result.shiftId());
        assertNull(result.message());
        assertEquals(expected.stream().map(e -> new GetBusSafetyHistoryResult.Item(
            PublicReference.of(e.getId()), "DRIVER", e.getReceivedAt(), e.getStatus().name())).toList(), result.items());
        Map<?, ?> body = objectMapper.readValue(objectMapper.writeValueAsString(result), Map.class);
        for (Object item : (List<?>) body.get("items")) {
            assertEquals(Set.of("reference", "source", "recordedAt", "state"), ((Map<?, ?>) item).keySet());
        }
        String json = objectMapper.writeValueAsString(result);
        emergencyIds.forEach(id -> assertFalse(json.contains(id)));
        assertFalse(json.contains(OUTCOME));
        assertFalse(json.contains(USER_RESPONSE));
        assertUnchanged(before);
    }

    @Test
    void getForBus_emptyHistoryStatesOnlyThatNoReportsAreRecorded() {
        journey(USER_ID, busId, false);
        Snapshot before = snapshot();

        var result = service.getForBus(busId);

        assertEquals(busId, result.busId());
        assertEquals(SHIFT_ID, result.shiftId());
        assertTrue(result.items().isEmpty());
        assertEquals("No reports are recorded for this bus and shift.", result.message());
        assertUnchanged(before);
    }

    @Test
    void getForBus_onlyOtherBusAndShiftRecordsStillReturnsEmptyHistory() {
        journey(USER_ID, busId, false);
        emergency(otherBusId, SHIFT_ID, 10, EmergencyStatus.ACTIVE);
        emergency(busId, SHIFT_ID + 1, 20, EmergencyStatus.ACTIVE);
        Snapshot before = snapshot();

        var result = service.getForBus(busId);

        assertTrue(result.items().isEmpty());
        assertEquals("No reports are recorded for this bus and shift.", result.message());
        assertUnchanged(before);
    }

    @Test
    void getForBus_anotherBusIsDenied() {
        journey(USER_ID, otherBusId, false);
        seedHistory();
        denied(busId);
    }

    @Test
    void getForBus_endedJourneyIsDenied() {
        journey(USER_ID, busId, true);
        seedHistory();
        denied(busId);
    }

    @Test
    void getForBus_noJourneyIsDenied() {
        seedHistory();
        denied(busId);
    }

    @Test
    void getForBus_anotherPassengersActiveJourneyDoesNotGrantAccess() {
        journey(USER_ID + 1, busId, false);
        seedHistory();
        denied(busId);
    }

    @Test
    void getForBus_nonexistentBusAndOtherBusHaveSameCodeAndMessage() {
        journey(USER_ID, busId, false);
        seedHistory();
        assertFalse(busRepository.existsById(Long.MAX_VALUE));

        var other = denied(otherBusId);
        var missing = denied(Long.MAX_VALUE);

        assertEquals(other.code(), missing.code());
        assertEquals(other.getMessage(), missing.getMessage());
    }

    private ForbiddenOperationException denied(Long requestedBusId) {
        Snapshot before = snapshot();
        var exception = assertThrows(ForbiddenOperationException.class, () -> service.getForBus(requestedBusId));
        assertEquals("BUS_ACCESS_DENIED", exception.code());
        assertEquals("access denied", exception.getMessage());
        assertUnchanged(before);
        return exception;
    }
}

