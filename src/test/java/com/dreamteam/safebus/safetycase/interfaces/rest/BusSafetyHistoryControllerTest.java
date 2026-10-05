package com.dreamteam.safebus.safetycase.interfaces.rest;

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

import com.dreamteam.safebus.safetycase.application.PublicReference;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BusSafetyHistoryControllerTest {
    @Autowired MockMvc mvc;

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
    }

    private JwtRequestPostProcessor caller(Long userId, String role) {
        return jwt().jwt(token -> token.subject(userId.toString()).claim("role", role))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private String path(Long id) {
        return "/api/v1/vehicles/" + id + "/safety-history";
    }

    @Test
    void get_activeJourneyReturnsThreeStatesNewestFirstAndOnlyPublicFields() throws Exception {
        journey(USER_ID, busId, false);
        List<Emergency> expected = seedHistory();
        Snapshot before = snapshot();

        MvcResult response = mvc.perform(get(path(busId)).with(caller(USER_ID, "PASSENGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(busId))
            .andExpect(jsonPath("$.shiftId").value(SHIFT_ID))
            .andExpect(jsonPath("$.items.length()").value(3))
            .andReturn();

        String json = response.getResponse().getContentAsString();
        Map<?, ?> body = objectMapper.readValue(json, Map.class);
        assertEquals(Set.of("busId", "shiftId", "items", "message"), body.keySet());
        assertNull(body.get("message"));
        List<?> items = (List<?>) body.get("items");
        for (int i = 0; i < expected.size(); i++) {
            Map<?, ?> item = (Map<?, ?>) items.get(i);
            Emergency emergency = expected.get(i);
            assertEquals(Set.of("reference", "source", "recordedAt", "state"), item.keySet());
            assertEquals(PublicReference.of(emergency.getId()), item.get("reference"));
            assertTrue(item.get("reference").toString().matches("EM-[0-9A-F]{8}"));
            assertEquals("DRIVER", item.get("source"));
            assertEquals(emergency.getReceivedAt(), Instant.parse(item.get("recordedAt").toString()));
            assertEquals(emergency.getStatus().name(), item.get("state"));
        }
        emergencyIds.forEach(id -> assertFalse(json.contains(id)));
        assertFalse(json.contains(OUTCOME));
        assertFalse(json.contains(USER_RESPONSE));
        assertUnchanged(before);
    }

    @Test
    void get_activeJourneyWithNoEmergenciesReturnsExactEmptyMessage() throws Exception {
        journey(USER_ID, busId, false);
        Snapshot before = snapshot();

        mvc.perform(get(path(busId)).with(caller(USER_ID, "PASSENGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(busId))
            .andExpect(jsonPath("$.shiftId").value(SHIFT_ID))
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.message").value("No reports are recorded for this bus and shift."));

        assertUnchanged(before);
    }

    @Test
    void get_onlyOtherBusAndShiftEmergenciesReturnsEmptyHistory() throws Exception {
        journey(USER_ID, busId, false);
        emergency(otherBusId, SHIFT_ID, 40, EmergencyStatus.ACTIVE);
        emergency(busId, SHIFT_ID + 1, 50, EmergencyStatus.ACTIVE);
        Snapshot before = snapshot();

        mvc.perform(get(path(busId)).with(caller(USER_ID, "PASSENGER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.message").value("No reports are recorded for this bus and shift."));

        assertUnchanged(before);
    }

    @Test
    void get_journeyOnAnotherBusReturnsForbidden() throws Exception {
        journey(USER_ID, otherBusId, false);
        seedHistory();
        denied(busId);
    }

    @Test
    void get_endedJourneyReturnsForbidden() throws Exception {
        journey(USER_ID, busId, true);
        seedHistory();
        denied(busId);
    }

    @Test
    void get_noJourneyReturnsForbidden() throws Exception {
        seedHistory();
        denied(busId);
    }

    @Test
    void get_anotherPassengersJourneyDoesNotGrantAccess() throws Exception {
        journey(USER_ID + 1, busId, false);
        seedHistory();
        denied(busId);
    }

    @Test
    void get_nonexistentBusAndOtherBusReturnSameStatusCodeAndDetail() throws Exception {
        journey(USER_ID, busId, false);
        seedHistory();
        assertFalse(busRepository.existsById(Long.MAX_VALUE));

        var other = denied(otherBusId);
        var missing = denied(Long.MAX_VALUE);

        assertEquals(other.getResponse().getStatus(), missing.getResponse().getStatus());
        Map<?, ?> otherBody = objectMapper.readValue(other.getResponse().getContentAsString(), Map.class);
        Map<?, ?> missingBody = objectMapper.readValue(missing.getResponse().getContentAsString(), Map.class);
        assertEquals(otherBody.get("status"), missingBody.get("status"));
        assertEquals(otherBody.get("code"), missingBody.get("code"));
        assertEquals(otherBody.get("detail"), missingBody.get("detail"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"DRIVER", "SUPERVISOR"})
    void get_otherRoleReturnsForbiddenEvenWithActiveJourney(String role) throws Exception {
        journey(USER_ID, busId, false);
        seedHistory();
        Snapshot before = snapshot();

        mvc.perform(get(path(busId)).with(caller(USER_ID, role)))
            .andExpect(status().isForbidden());

        assertUnchanged(before);
    }

    @Test
    void get_withoutTokenReturnsUnauthorized() throws Exception {
        journey(USER_ID, busId, false);
        seedHistory();
        Snapshot before = snapshot();

        mvc.perform(get(path(busId))).andExpect(status().isUnauthorized());

        assertUnchanged(before);
    }

    private MvcResult denied(Long requestedBusId) throws Exception {
        Snapshot before = snapshot();
        MvcResult response = mvc.perform(get(path(requestedBusId)).with(caller(USER_ID, "PASSENGER")))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"))
            .andExpect(jsonPath("$.detail").value("access denied"))
            .andReturn();
        assertUnchanged(before);
        return response;
    }
}

