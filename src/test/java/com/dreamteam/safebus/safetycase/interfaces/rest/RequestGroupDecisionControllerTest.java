package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class RequestGroupDecisionControllerTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 72001L;
    private static final long OTHER_SUPERVISOR = 72002L;
    private static final long U1 = 72101L;
    private static final long U2 = 72102L;
    private static final long U3 = 72103L;
    private static final long U4 = 72104L;
    private static final long U5 = 72105L;
    private static final byte[] PHOTO = TestImages.jpeg(7);

    @Autowired MockMvc mockMvc;

    private RequestPostProcessor token(long userId, String role, Long companyId) {
        return jwt().jwt(b -> {
            b.subject(String.valueOf(userId)).claim("role", role);
            if (companyId != null) {
                b.claim("companyId", companyId);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private RequestPostProcessor supervisor() {
        return token(SUPERVISOR, "SUPERVISOR", company.getId());
    }

    private ResultActions approve(Object groupId, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(post("/api/v1/request-groups/" + groupId + "/approve").with(auth));
    }

    private ResultActions decline(Object groupId, RequestPostProcessor auth, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/request-groups/" + groupId + "/decline").with(auth)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions submitRequest(long userId) throws Exception {
        return mockMvc.perform(multipart("/api/v1/passenger-requests")
            .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", PHOTO))
            .param("id", UUID.randomUUID().toString())
            .param("journeyId", String.valueOf(activeJourney(userId).getId()))
            .param("message", "Danger on board")
            .param("activatedAt", clock.instant().minusSeconds(5).toString())
            .with(token(userId, "PASSENGER", null)));
    }

    private PassengerRequestGroup awaitingGroupFromRequests() throws Exception {
        submitRequest(U1).andExpect(status().isCreated());
        submitRequest(U2).andExpect(status().isCreated());
        submitRequest(U3).andExpect(status().isCreated())
            .andExpect(jsonPath("$.groupStatus").value("AWAITING_APPROVAL"));
        return groupsOfShift().getFirst();
    }

    private static String withoutInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    private PassengerRequestGroup reload(PassengerRequestGroup group) {
        return groupRepository.findById(group.getId()).orElseThrow();
    }

    private List<Emergency> emergenciesOf(PassengerRequestGroup group) {
        return emergencyRepository.findAll().stream()
            .filter(e -> group.getId().equals(e.getGroupId())).toList();
    }

    // --- US10 S2: approve a qualifying passenger group ---

    @Test
    void approveActivatesOneHighEmergencyAndMovesTheGroupToApproved() throws Exception {
        PassengerRequestGroup group = awaitingGroupFromRequests();

        String emergencyId = approve(group.getId(), supervisor()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.groupId").value(group.getId()))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.priority").value("HIGH"))
            .andExpect(jsonPath("$.approvedAt").isString())
            .andExpect(jsonPath("$.emergencyId").isString())
            .andReturn().getResponse().getContentAsString().replaceAll(".*\"emergencyId\":\"([^\"]+)\".*", "$1");

        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
        assertEquals(SUPERVISOR, reload(group).getDecidedByUserId());
        assertEquals(1, emergenciesOf(group).size());
        assertEquals(emergencyId, emergenciesOf(group).getFirst().getId());
        assertEquals(3, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));
    }

    @Test
    void approvingAgainReturns200WithTheSameEmergency() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        approve(group.getId(), supervisor()).andExpect(status().isCreated());
        String emergencyId = emergenciesOf(group).getFirst().getId();

        approve(group.getId(), token(OTHER_SUPERVISOR, "SUPERVISOR", company.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emergencyId").value(emergencyId))
            .andExpect(jsonPath("$.status").value("APPROVED"));

        assertEquals(1, emergenciesOf(group).size());
    }

    @Test
    void approvingANotApprovedClosedOrCollectingGroupIsAConflict() throws Exception {
        for (GroupStatus status : List.of(GroupStatus.COLLECTING, GroupStatus.EXPIRED,
                GroupStatus.NOT_APPROVED, GroupStatus.CLOSED)) {
            PassengerRequestGroup group = groupInStatus(status);

            approve(group.getId(), supervisor()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));

            assertEquals(0, emergenciesOf(group).size());
            groupRepository.delete(group);
        }
    }

    @Test
    void aForeignAndAMissingGroupGiveTheSame403() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        RequestPostProcessor foreign = token(SUPERVISOR, "SUPERVISOR", company.getId() + 1_000_000);

        String foreignBody = approve(group.getId(), foreign).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String missingBody = approve(Long.MAX_VALUE, foreign).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String malformedBody = approve("not-a-number", foreign).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(withoutInstance(foreignBody), withoutInstance(missingBody));
        assertEquals(withoutInstance(foreignBody), withoutInstance(malformedBody));
        decline(group.getId(), foreign, "{\"reason\":\"x\"}").andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
        assertEquals(0, emergenciesOf(group).size());
    }

    @Test
    void onlyASupervisorMayDecideAndAnUnauthenticatedCallIs401() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        approve(group.getId(), token(1L, "PASSENGER", null)).andExpect(status().isForbidden());
        approve(group.getId(), token(1L, "DRIVER", company.getId())).andExpect(status().isForbidden());
        decline(group.getId(), token(1L, "PASSENGER", null), "{\"reason\":\"x\"}")
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve"))
            .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/decline")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}"))
            .andExpect(status().isUnauthorized());

        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
    }

    @Test
    void aSupervisorTokenWithoutCompanyIsRefused() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        approve(group.getId(), token(SUPERVISOR, "SUPERVISOR", null)).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("GROUP_ACCESS_DENIED"));
    }

    // --- US10 S3: decline / prevent premature approval ---

    @Test
    void declineRecordsNotApprovedWithReasonAndResponseAndCreatesNoEmergency() throws Exception {
        PassengerRequestGroup group = awaitingGroupFromRequests();

        decline(group.getId(), supervisor(), "{\"reason\":\"Not credible\",\"response\":\"Thank you\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.groupId").value(group.getId()))
            .andExpect(jsonPath("$.status").value("NOT_APPROVED"))
            .andExpect(jsonPath("$.reason").value("Not credible"))
            .andExpect(jsonPath("$.response").value("Thank you"))
            .andExpect(jsonPath("$.decidedAt").isString());

        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.NOT_APPROVED, stored.getStatus());
        assertEquals("Not credible", stored.getDecisionReason());
        assertEquals("Thank you", stored.getUserResponse());
        assertNull(stored.getOpenMarker());
        assertEquals(0, emergenciesOf(group).size());
        assertEquals(3, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));
        assertEquals(3, storedImagesAdded());
    }

    @Test
    void decliningTwiceIsIdempotentAndKeepsTheFirstDecision() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        decline(group.getId(), supervisor(), "{\"reason\":\"First\",\"response\":\"r1\"}")
            .andExpect(status().isOk());

        decline(group.getId(), token(OTHER_SUPERVISOR, "SUPERVISOR", company.getId()), "{\"reason\":\"Second\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.reason").value("First"))
            .andExpect(jsonPath("$.response").value("r1"));

        assertEquals(SUPERVISOR, reload(group).getDecidedByUserId());
    }

    @Test
    void approvingANotApprovedGroupAndDecliningAnApprovedOneAreConflicts() throws Exception {
        PassengerRequestGroup declined = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        decline(declined.getId(), supervisor(), "{\"reason\":\"No\"}").andExpect(status().isOk());
        approve(declined.getId(), supervisor()).andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertEquals(0, emergenciesOf(declined).size());
        groupRepository.deleteById(declined.getId());

        PassengerRequestGroup approved = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        approve(approved.getId(), supervisor()).andExpect(status().isCreated());
        decline(approved.getId(), supervisor(), "{\"reason\":\"No\"}").andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        assertEquals(GroupStatus.APPROVED, reload(approved).getStatus());
        assertEquals(1, emergenciesOf(approved).size());
    }

    @Test
    void declineWithoutAValidReasonIs422() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        decline(group.getId(), supervisor(), "{\"reason\":\"  \"}").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        decline(group.getId(), supervisor(), "{}").andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/decline").with(supervisor()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        decline(group.getId(), supervisor(), "{\"reason\":\"" + "x".repeat(501) + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("REASON_TOO_LONG"));
        decline(group.getId(), supervisor(), "{\"reason\":\"ok\",\"response\":\"" + "y".repeat(501) + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("RESPONSE_TOO_LONG"));

        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
    }

    @Test
    void approvingAGroupBelowThresholdOrExpiredIsRejected() throws Exception {
        submitRequest(U1).andExpect(status().isCreated());
        PassengerRequestGroup collecting = groupsOfShift().getFirst();

        approve(collecting.getId(), supervisor()).andExpect(status().isConflict());

        assertEquals(GroupStatus.COLLECTING, reload(collecting).getStatus());
        assertEquals(0, emergencyRepository.count());
    }

    // --- US08 S6 / S4 interplay ---

    @Test
    void afterADeclineTheShiftIsFreeAndANewAccountStartsANewGroup() throws Exception {
        PassengerRequestGroup declined = awaitingGroupFromRequests();
        decline(declined.getId(), supervisor(), "{\"reason\":\"No\"}").andExpect(status().isOk());

        submitRequest(U4).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COUNTED"))
            .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
            .andExpect(jsonPath("$.contributorCount").value(1));

        assertEquals(2, groupsOfShift().size());
        assertEquals(GroupStatus.NOT_APPROVED, groupsOfShift().get(0).getStatus());
        PassengerRequestGroup fresh = groupsOfShift().get(1);
        assertEquals(GroupStatus.COLLECTING, fresh.getStatus());
        assertEquals(1, fresh.getOpenMarker());
        assertEquals(3, requestRepository.countByGroupIdAndStatus(declined.getId(), RequestStatus.COUNTED));
    }

    @Test
    void requestsAfterAnApprovalStayAttachedToTheApprovedGroup() throws Exception {
        PassengerRequestGroup group = awaitingGroupFromRequests();
        approve(group.getId(), supervisor()).andExpect(status().isCreated());

        submitRequest(U4).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ATTACHED"))
            .andExpect(jsonPath("$.groupStatus").value("APPROVED"));
        submitRequest(U5).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("ATTACHED"));

        assertEquals(1, groupsOfShift().size());
        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
        assertEquals(1, emergenciesOf(group).size());
        List<PassengerRequest> attached = requestRepository.findAll().stream()
            .filter(r -> r.getStatus() == RequestStatus.ATTACHED).toList();
        assertEquals(2, attached.size());
        assertTrue(attached.stream().allMatch(r -> group.getId().equals(r.getGroupId())));
    }
}
