package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class MyPassengerRequestControllerTest extends AbstractPassengerRequestTest {

    private static final String MINE = "/api/v1/passenger-requests/mine";
    private static final long SUPERVISOR = 78001L;
    private static final long U1 = 78101L;
    private static final long U2 = 78102L;
    private static final long U3 = 78103L;
    private static final String DECLINE_REASON = "INTERNAL-REASON-KEEP-PRIVATE";
    private static final String DECLINE_RESPONSE = "We could not corroborate the report.";
    private static final String CLOSE_OUTCOME = "INTERNAL-OUTCOME-KEEP-PRIVATE";
    private static final String CLOSE_RESPONSE = "The situation was resolved by the company.";
    private static final String OTHER_MESSAGE = "OTHER-PASSENGER-PRIVATE-MESSAGE";

    private static final List<String> FORBIDDEN_KEYS = List.of(
        "decisionReason", "decision_reason", "reason", "outcome", "internalNotes", "notes", "decidedBy",
        "decidedByUserId", "supervisor", "supervisorId", "responsibleSupervisorUserId", "groupId", "journeyId",
        "busId", "shiftId", "companyId", "routeId", "evidenceImageId", "imageId", "image", "photo", "facePhoto",
        "faceImage", "registrationPhoto", "dni", "passengerUserId", "userAccountId", "userId", "passengerId",
        "evidenceSha256", "sha256", "version", "openMarker", "emergencyId");

    @Autowired MockMvc mockMvc;

    private static RequestPostProcessor passenger(long userId) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private RequestPostProcessor supervisor() {
        return jwt().jwt(b -> b.subject(String.valueOf(SUPERVISOR)).claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private String submit(long userId, PassengerJourney journey, String message) throws Exception {
        return submit(userId, journey, message, clock.instant().minusSeconds(5));
    }

    private String submit(long userId, PassengerJourney journey, String message, Instant activatedAt)
        throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(7)))
                .param("id", id)
                .param("journeyId", String.valueOf(journey.getId()))
                .param("message", message)
                .param("activatedAt", activatedAt.toString())
                .with(passenger(userId)))
            .andExpect(status().isCreated());
        return id;
    }

    private String submit(long userId) throws Exception {
        return submit(userId, activeJourney(userId), "Danger on board of " + userId);
    }

    private ResultActions read(long userId, String id) throws Exception {
        return mockMvc.perform(get(MINE + "/" + id).with(passenger(userId)));
    }

    private PassengerRequestGroup group() {
        return groupsOfShift().getFirst();
    }

    private Emergency emergencyOf(PassengerRequestGroup group) {
        return emergencyRepository.findByGroupId(group.getId()).orElseThrow();
    }

    private void awaitApproval() throws Exception {
        submit(U2);
        submit(U3);
    }

    private void approve(PassengerRequestGroup group) throws Exception {
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated());
    }

    private static String strip(String body) {
        return body.replaceAll("\"instance\":\"[^\"]*\"", "");
    }

    private void assertNoForbiddenData(String body) {
        for (String key : FORBIDDEN_KEYS) {
            assertFalse(body.contains("\"" + key + "\":"), "response must not carry the key " + key + ": " + body);
        }
        for (String value : List.of(DECLINE_REASON, CLOSE_OUTCOME, OTHER_MESSAGE, String.valueOf(SUPERVISOR),
                "Danger on board of " + U2, "Danger on board of " + U3)) {
            assertFalse(body.contains(value), "response must not carry " + value + ": " + body);
        }
    }

    // --- US09 S1: status table ---

    @Test
    void aFirstRequestIsCollectingWithItsOwnMessageEvidenceReferenceAndCount() throws Exception {
        String id = submit(U1);

        read(U1, id).andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(id))
            .andExpect(jsonPath("$.message").value("Danger on board of " + U1))
            .andExpect(jsonPath("$.evidenceReference").value(MINE + "/" + id + "/photo"))
            .andExpect(jsonPath("$.submittedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.groupReference").value(org.hamcrest.Matchers.matchesPattern("GR-[0-9A-F]{8}")))
            .andExpect(jsonPath("$.contributorCount").value(1))
            .andExpect(jsonPath("$.status").value("COLLECTING"))
            .andExpect(jsonPath("$.decision").doesNotExist());
    }

    @Test
    void theContributorCountIsTheGroupsDistinctCountedRequests() throws Exception {
        String id = submit(U1);
        submit(U2);

        read(U1, id).andExpect(jsonPath("$.contributorCount").value(2));
    }

    @Test
    void aCollectingGroupPastItsWindowIsReportedExpiredBeforeTheJobRuns() throws Exception {
        String id = submit(U1);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));

        read(U1, id).andExpect(jsonPath("$.status").value("EXPIRED")).andExpect(jsonPath("$.decision").doesNotExist());
        assertEquals(GroupStatus.COLLECTING, group().getStatus());
    }

    @Test
    void anExpiredGroupIsReportedExpired() throws Exception {
        String id = submit(U1);
        clock.advance(Duration.ofMinutes(6));
        PassengerRequestGroup expired = group();
        expired.expire();
        groupRepository.saveAndFlush(expired);

        read(U1, id).andExpect(jsonPath("$.status").value("EXPIRED"));
    }

    @Test
    void aGroupWithThreeContributorsIsAwaitingCompanyApproval() throws Exception {
        String id = submit(U1);
        awaitApproval();

        read(U1, id).andExpect(jsonPath("$.status").value("AWAITING_APPROVAL"))
            .andExpect(jsonPath("$.contributorCount").value(3))
            .andExpect(jsonPath("$.decision").doesNotExist());
    }

    @Test
    void aLateRequestIsLateWithoutAGroupOrACount() throws Exception {
        Instant endedAt = clock.instant().plusSeconds(1);
        PassengerJourney ended = endedJourney(U1, endedAt);
        clock.advance(Duration.ofMinutes(1));
        String id = submit(U1, ended, "Late report", endedAt.minusSeconds(10));

        read(U1, id).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("LATE"))
            .andExpect(jsonPath("$.groupReference").doesNotExist())
            .andExpect(jsonPath("$.contributorCount").doesNotExist())
            .andExpect(jsonPath("$.decision").doesNotExist());
    }

    // --- US09 S2: decisions, after the journey ended ---

    @Test
    void aDeclinedGroupReportsNotApprovedWithTheDecisionTimeAndTheUserFacingResponseOnly() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = submit(U1, journey, "Danger on board of " + U1);
        awaitApproval();
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(post("/api/v1/request-groups/" + group().getId() + "/decline")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"" + DECLINE_REASON + "\",\"response\":\"" + DECLINE_RESPONSE + "\"}")
                .with(supervisor()))
            .andExpect(status().isOk());
        clock.advance(Duration.ofMinutes(1));
        mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
                .with(passenger(U1)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"MANUAL\"}"))
            .andExpect(status().isOk());

        String body = read(U1, id).andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NOT_APPROVED"))
            .andExpect(jsonPath("$.decision.result").value("NOT_APPROVED"))
            .andExpect(jsonPath("$.decision.decidedAt").value(group().getDecidedAt().toString()))
            .andExpect(jsonPath("$.decision.response").value(DECLINE_RESPONSE))
            .andReturn().getResponse().getContentAsString();

        assertNoForbiddenData(body);
    }

    @Test
    void aDeclinedGroupWithoutAResponseReportsANullResponse() throws Exception {
        String id = submit(U1);
        awaitApproval();
        mockMvc.perform(post("/api/v1/request-groups/" + group().getId() + "/decline")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + DECLINE_REASON + "\"}")
                .with(supervisor()))
            .andExpect(status().isOk());

        read(U1, id).andExpect(jsonPath("$.decision.result").value("NOT_APPROVED"))
            .andExpect(jsonPath("$.decision.response").doesNotExist());
    }

    @Test
    void anApprovedGroupReportsAnActiveEmergencyAndNoResponseYet() throws Exception {
        String id = submit(U1);
        awaitApproval();
        clock.advance(Duration.ofMinutes(1));
        approve(group());

        String body = read(U1, id).andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.decision.result").value("APPROVED"))
            .andExpect(jsonPath("$.decision.decidedAt").value(group().getDecidedAt().toString()))
            .andExpect(jsonPath("$.decision.response").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertNoForbiddenData(body);
    }

    @Test
    void anEmergencyUnderAttentionIsInProgressAndStillHasNoResponse() throws Exception {
        String id = submit(U1);
        awaitApproval();
        approve(group());
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyOf(group()).getId() + "/start-attention")
                .with(supervisor()))
            .andExpect(status().isOk());

        read(U1, id).andExpect(jsonPath("$.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.decision.result").value("APPROVED"))
            .andExpect(jsonPath("$.decision.response").doesNotExist());
    }

    @Test
    void aClosedEmergencyReportsClosedWithOnlyTheUserFacingResponseNeverTheOutcome() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = submit(U1, journey, "Danger on board of " + U1);
        awaitApproval();
        approve(group());
        String emergencyId = emergencyOf(group()).getId();
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/start-attention").with(supervisor()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/close")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"outcome\":\"" + CLOSE_OUTCOME + "\",\"response\":\"" + CLOSE_RESPONSE + "\"}")
                .with(supervisor()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
                .with(passenger(U1)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"SIGN_OUT\"}"))
            .andExpect(status().isOk());

        String body = read(U1, id).andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.decision.result").value("APPROVED"))
            .andExpect(jsonPath("$.decision.response").value(CLOSE_RESPONSE))
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(CLOSE_OUTCOME));
        assertNoForbiddenData(body);
    }

    @Test
    void aRequestAttachedAfterApprovalFollowsTheEmergencyWithoutChangingTheCount() throws Exception {
        submit(U1);
        awaitApproval();
        approve(group());
        String attached = submit(U1 + 10);

        read(U1 + 10, attached).andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.contributorCount").value(3));
    }

    // --- list ---

    @Test
    void theListHoldsOnlyTheCallersRequestsNewestFirstWithoutAnActiveJourney() throws Exception {
        PassengerJourney first = activeJourney(U1);
        String older = submit(U1, first, "Older report");
        submit(U2, activeJourney(U2), OTHER_MESSAGE);
        clock.advance(Duration.ofMinutes(6));
        mockMvc.perform(post("/api/v1/journeys/" + first.getId() + "/end")
                .with(passenger(U1)).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"MANUAL\"}"))
            .andExpect(status().isOk());
        PassengerJourney second = activeJourney(U1);
        String newer = submit(U1, second, "Newer report");

        String body = mockMvc.perform(get(MINE).with(passenger(U1))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].requestId").value(newer))
            .andExpect(jsonPath("$.items[1].requestId").value(older))
            .andExpect(jsonPath("$.message").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains(OTHER_MESSAGE));
        assertNoForbiddenData(body);
    }

    @Test
    void anAccountWithoutRequestsGetsAnEmptyListAndAMessage() throws Exception {
        submit(U2);

        mockMvc.perform(get(MINE).with(passenger(U1))).andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.message").value("No requests are recorded for this account."));
    }

    // --- US09 S3: another account ---

    @Test
    void anotherAccountsMissingAndMalformedIdsAreAnsweredWithTheSame403() throws Exception {
        String others = submit(U2);

        String foreign = read(U1, others).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSENGER_REQUEST_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String missing = read(U1, UUID.randomUUID().toString()).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String malformed = read(U1, "not-a-uuid").andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(strip(foreign), strip(missing));
        assertEquals(strip(foreign), strip(malformed));
        assertFalse(foreign.contains("Danger on board"));
        assertTrue(foreign.contains("PASSENGER_REQUEST_ACCESS_DENIED"));
    }

    @Test
    void anUppercaseIdOfTheCallersOwnRequestIsAccepted() throws Exception {
        String id = submit(U1);

        read(U1, id.toUpperCase()).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value(id));
    }

    // --- auth ---

    @Test
    void onlyPassengersAreServedAndAnonymousCallersGet401() throws Exception {
        String id = submit(U1);

        mockMvc.perform(get(MINE)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE).with(supervisor())).andExpect(status().isForbidden())
            .andExpect(content().string(""));
        mockMvc.perform(get(MINE + "/" + id).with(supervisor())).andExpect(status().isForbidden())
            .andExpect(content().string(""));
    }
}
