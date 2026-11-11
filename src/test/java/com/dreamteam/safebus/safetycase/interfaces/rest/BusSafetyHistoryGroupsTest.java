package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.application.PublicReference;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestPolicy;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class BusSafetyHistoryGroupsTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 80001L;
    private static final long U1 = 80101L;
    private static final long U2 = 80102L;
    private static final long U3 = 80103L;
    private static final String DECLINE_REASON = "INTERNAL-REASON-KEEP-PRIVATE";
    private static final String DECLINE_RESPONSE = "We could not corroborate the report.";
    private static final String CLOSE_OUTCOME = "INTERNAL-OUTCOME-KEEP-PRIVATE";
    private static final String CLOSE_RESPONSE = "The situation was resolved by the company.";
    private static final Set<String> TOP_LEVEL_KEYS = Set.of("busId", "shiftId", "items", "message");
    private static final Set<String> ITEM_KEYS =
        Set.of("reference", "source", "recordedAt", "state", "stateLabel", "requestCount");
    private static final List<String> FORBIDDEN_KEYS = List.of(
        "id", "groupId", "emergencyId", "journeyId", "companyId", "routeId", "driverId", "decisionReason",
        "decision_reason", "reason", "outcome", "userResponse", "response", "internalNotes", "notes", "decidedBy",
        "supervisorId", "responsibleSupervisorUserId", "evidenceImageId", "imageId", "image", "photo",
        "faceImage", "registrationPhoto", "dni", "passengerUserId", "userAccountId", "userId", "message_text",
        "publicReference", "public_reference", "openMarker", "version");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private static RequestPostProcessor passenger(long userId) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private RequestPostProcessor supervisor() {
        return jwt().jwt(b -> b.subject(String.valueOf(SUPERVISOR)).claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private void submit(long userId, PassengerJourney journey) throws Exception {
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(5)))
                .param("id", UUID.randomUUID().toString())
                .param("journeyId", String.valueOf(journey.getId()))
                .param("message", "Danger on board of " + userId)
                .param("activatedAt", clock.instant().minusSeconds(5).toString())
                .with(passenger(userId)))
            .andExpect(status().isCreated());
    }

    private PassengerJourney collecting() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        submit(U1, journey);
        return journey;
    }

    private void awaitApproval() throws Exception {
        submit(U2, activeJourney(U2));
        submit(U3, activeJourney(U3));
    }

    private PassengerRequestGroup group() {
        return groupsOfShift().getFirst();
    }

    private void approve() throws Exception {
        mockMvc.perform(post("/api/v1/request-groups/" + group().getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated());
    }

    private Emergency groupEmergency() {
        return emergencyRepository.findByGroupId(group().getId()).orElseThrow();
    }

    private ResultActions history(String busId, String query) throws Exception {
        return mockMvc.perform(get("/api/v1/vehicles/" + busId + "/safety-history" + query).with(passenger(U1)));
    }

    private String historyBody() throws Exception {
        return history(String.valueOf(bus.getId()), "").andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(String body) throws Exception {
        return (List<Map<String, Object>>) objectMapper.readValue(body, Map.class).get("items");
    }

    private void assertOnlyPublicData(String body) throws Exception {
        Map<?, ?> parsed = objectMapper.readValue(body, Map.class);
        assertTrue(TOP_LEVEL_KEYS.containsAll(parsed.keySet()), "unexpected top-level keys: " + parsed.keySet());
        for (Map<String, Object> item : items(body)) {
            assertTrue(ITEM_KEYS.containsAll(item.keySet()), "unexpected item keys: " + item.keySet());
        }
        for (String key : FORBIDDEN_KEYS) {
            assertFalse(body.contains("\"" + key + "\":"), "history must not carry the key " + key + ": " + body);
        }
        List<String> values = new java.util.ArrayList<>(List.of(DECLINE_REASON, DECLINE_RESPONSE, CLOSE_OUTCOME,
            CLOSE_RESPONSE, "Danger on board", String.valueOf(SUPERVISOR)));
        groupsOfShift().forEach(g -> values.add(g.getPublicReference()));
        emergencyRepository.findAll().forEach(e -> values.add(e.getId()));
        for (String value : values) {
            assertFalse(body.contains(value), "history must not carry " + value + ": " + body);
        }
    }

    private static String withoutInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    // --- groups in the history ---

    @Test
    void aCollectingGroupIsOneItemWithItsStableReferenceAndRequestCount() throws Exception {
        collecting();

        String body = historyBody();

        assertEquals(1, items(body).size());
        Map<String, Object> item = items(body).getFirst();
        assertEquals(PublicReference.ofGroup(group().getId(), group().getPublicReference()), item.get("reference"));
        assertTrue(item.get("reference").toString().matches("GR-[0-9A-F]{8}"));
        assertEquals("PASSENGER_GROUP", item.get("source"));
        assertEquals("COLLECTING", item.get("state"));
        assertEquals("Collecting requests", item.get("stateLabel"));
        assertEquals(1, item.get("requestCount"));
        assertEquals(group().getWindowStartedAt(), Instant.parse(item.get("recordedAt").toString()));
        assertNull(objectMapper.readValue(body, Map.class).get("message"));
        assertOnlyPublicData(body);
    }

    @Test
    void anOverdueCollectingGroupIsReportedExpiredBeforeTheExpiryJobRuns() throws Exception {
        collecting();
        clock.advance(PassengerRequestPolicy.WINDOW.plusSeconds(1));

        Map<String, Object> item = items(historyBody()).getFirst();

        assertEquals("EXPIRED", item.get("state"));
        assertEquals("Expired", item.get("stateLabel"));
        assertEquals(GroupStatus.COLLECTING, group().getStatus());
    }

    @Test
    void aGroupWithThreeContributorsIsAwaitingCompanyApproval() throws Exception {
        collecting();
        awaitApproval();

        Map<String, Object> item = items(historyBody()).getFirst();

        assertEquals("AWAITING_APPROVAL", item.get("state"));
        assertEquals("Awaiting company approval", item.get("stateLabel"));
        assertEquals(3, item.get("requestCount"));
    }

    @Test
    void aDeclinedGroupIsShownAsNotApprovedWithoutAnyReasonOrResponse() throws Exception {
        collecting();
        awaitApproval();
        mockMvc.perform(post("/api/v1/request-groups/" + group().getId() + "/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + DECLINE_REASON + "\",\"response\":\"" + DECLINE_RESPONSE + "\"}")
                .with(supervisor()))
            .andExpect(status().isOk());

        String body = historyBody();

        assertEquals(1, items(body).size());
        assertEquals("NOT_APPROVED", items(body).getFirst().get("state"));
        assertEquals("Not approved", items(body).getFirst().get("stateLabel"));
        assertOnlyPublicData(body);
    }

    // --- one item for a group with an emergency ---

    @Test
    void aGroupAndItsEmergencyAreOneItemWithTheSameReferenceThroughEveryState() throws Exception {
        collecting();
        awaitApproval();
        String awaitingReference = items(historyBody()).getFirst().get("reference").toString();
        clock.advance(Duration.ofMinutes(1));
        approve();

        String active = historyBody();
        assertEquals(1, items(active).size());
        Map<String, Object> item = items(active).getFirst();
        assertEquals(awaitingReference, item.get("reference"));
        assertTrue(item.get("reference").toString().startsWith("GR-"));
        assertEquals("PASSENGER_GROUP", item.get("source"));
        assertEquals("ACTIVE", item.get("state"));
        assertEquals("Active", item.get("stateLabel"));
        assertEquals(3, item.get("requestCount"));
        assertEquals(groupEmergency().getReceivedAt(), Instant.parse(item.get("recordedAt").toString()));
        assertFalse(active.contains("EM-"));
        assertOnlyPublicData(active);

        String emergencyId = groupEmergency().getId();
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/start-attention").with(supervisor()))
            .andExpect(status().isOk());
        Map<String, Object> inProgress = items(historyBody()).getFirst();
        assertEquals(1, items(historyBody()).size());
        assertEquals(awaitingReference, inProgress.get("reference"));
        assertEquals("IN_PROGRESS", inProgress.get("state"));
        assertEquals("In progress", inProgress.get("stateLabel"));

        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + CLOSE_OUTCOME + "\",\"response\":\"" + CLOSE_RESPONSE + "\"}")
                .with(supervisor()))
            .andExpect(status().isOk());
        String closed = historyBody();
        assertEquals(1, items(closed).size());
        assertEquals(awaitingReference, items(closed).getFirst().get("reference"));
        assertEquals("CLOSED", items(closed).getFirst().get("state"));
        assertEquals("Closed", items(closed).getFirst().get("stateLabel"));
        assertOnlyPublicData(closed);
    }

    @Test
    void aDriverEmergencyKeepsItsReferenceAndHasNoRequestCount() throws Exception {
        collecting();
        awaitApproval();
        approve();
        clock.advance(Duration.ofMinutes(1));
        Emergency driverEmergency = emergencyRepository.saveAndFlush(Emergency.activateByDriver(
            UUID.randomUUID().toString(), company.getId(), driver.getId(), bus.getId(), shift.getId(),
            route.getId(), new GeoPoint(-12.046, -77.042), clock.instant().minusSeconds(2),
            Clock.fixed(clock.instant(), java.time.ZoneOffset.UTC)));

        String body = historyBody();

        List<Map<String, Object>> items = items(body);
        assertEquals(2, items.size());
        Map<String, Object> newest = items.getFirst();
        assertEquals(PublicReference.of(driverEmergency.getId()), newest.get("reference"));
        assertEquals("DRIVER", newest.get("source"));
        assertFalse(newest.containsKey("requestCount"));
        assertEquals("ACTIVE", newest.get("state"));
        assertTrue(items.get(1).get("reference").toString().startsWith("GR-"));
        assertOnlyPublicData(body);
    }

    @Test
    void groupsOfAnotherShiftAreLeftOut() throws Exception {
        collecting();
        PassengerRequestGroup foreign = groupRepository.saveAndFlush(PassengerRequestGroup.open(
            company.getId(), bus.getId(), shift.getId() + 7_000_000L, clock.instant()));
        try {
            assertEquals(1, items(historyBody()).size());
        } finally {
            groupRepository.delete(foreign);
        }
    }

    @Test
    void anEmptyHistoryOnlyStatesThatNoReportsAreRecorded() throws Exception {
        activeJourney(U1);

        String body = historyBody();

        assertEquals(0, items(body).size());
        assertEquals("No reports are recorded for this bus and shift.",
            objectMapper.readValue(body, Map.class).get("message"));
        assertFalse(body.toLowerCase().contains("safe"));
        assertFalse(body.toLowerCase().contains("risk"));
    }

    // --- access ---

    @Test
    void aMatchingShiftIdIsAcceptedAndEveryOtherShiftOrMalformedInputGivesTheSame403() throws Exception {
        activeJourney(U1);
        String busId = String.valueOf(bus.getId());
        String shiftId = String.valueOf(shift.getId());

        history(busId, "?shiftId=" + shiftId).andExpect(status().isOk());

        String otherBus = history(String.valueOf(bus.getId() + 1_000_000L), "").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        for (ResultActions denied : List.of(
                history(busId, "?shiftId=" + (shift.getId() + 1)),
                history(busId, "?shiftId=abc"),
                history(busId, "?shiftId="),
                history("abc", ""),
                history("abc", "?shiftId=" + shiftId))) {
            String body = denied.andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
            assertEquals(withoutInstance(otherBus), withoutInstance(body));
        }
    }

    @Test
    void anEndedJourneyGivesTheSame403AsAnyOtherDenial() throws Exception {
        PassengerJourney journey = collecting();
        String busId = String.valueOf(bus.getId());
        String denied = history(String.valueOf(bus.getId() + 1_000_000L), "").andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
                .with(passenger(U1)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"MANUAL\"}"))
            .andExpect(status().isOk());

        String body = history(busId, "").andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(withoutInstance(denied), withoutInstance(body));
    }
}
