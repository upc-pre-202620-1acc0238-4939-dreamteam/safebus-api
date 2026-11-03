package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.safetycase.application.ExpireOverdueGroups;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class GroupsWithOtherSafetyRecordsTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 76001L;

    @Autowired MockMvc mockMvc;
    @Autowired ExpireOverdueGroups expireOverdueGroups;

    private RequestPostProcessor supervisor() {
        return jwt().jwt(b -> b.subject(String.valueOf(SUPERVISOR)).claim("role", "SUPERVISOR")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR"));
    }

    private RequestPostProcessor driverToken() {
        return jwt().jwt(b -> b.subject(String.valueOf(driver.getUserAccountId())).claim("role", "DRIVER")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private String panic() throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(post("/api/v1/driver-emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + id + "\",\"shiftId\":" + shift.getId()
                    + ",\"activatedAt\":\"" + clock.instant().minusSeconds(2) + "\"}")
                .with(driverToken()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.priority").value("CRITICAL"));
        return id;
    }

    private void closeShiftThroughTheEndpoint() throws Exception {
        ShiftAssignment active = assignmentRepository.findById(shift.getAssignmentId()).orElseThrow();
        active.activate();
        assignmentRepository.saveAndFlush(active);

        mockMvc.perform(post("/api/v1/shifts/" + shift.getId() + "/close").with(driverToken()))
            .andExpect(status().isOk());

        shift = shiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, shift.getStatus());
        assertEquals(AssignmentStatus.CLOSED,
            assignmentRepository.findById(shift.getAssignmentId()).orElseThrow().getStatus());
    }

    private void submitPassengerRequest(long userId) throws Exception {
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(5)))
                .param("id", UUID.randomUUID().toString())
                .param("journeyId", String.valueOf(activeJourney(userId).getId()))
                .param("message", "Danger on board")
                .param("activatedAt", clock.instant().minusSeconds(5).toString())
                .with(jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isCreated());
    }

    private PassengerRequestGroup reload(PassengerRequestGroup group) {
        return groupRepository.findById(group.getId()).orElseThrow();
    }

    private ResultActions queue() throws Exception {
        return mockMvc.perform(get("/api/v1/review-queue").with(supervisor()));
    }

    // --- US04 S3: a driver emergency never waits for, nor alters, a passenger group ---

    @Test
    void aDriverPanicIsCreatedImmediatelyWhileAGroupAwaitsApprovalAndTheGroupIsUntouched() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        String id = panic();

        Emergency driverEmergency = emergencyRepository.findById(id).orElseThrow();
        assertEquals(EmergencyStatus.ACTIVE, driverEmergency.getStatus());
        assertNull(driverEmergency.getGroupId());
        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.AWAITING_APPROVAL, stored.getStatus());
        assertEquals(1, stored.getOpenMarker());
        assertNull(stored.getDecidedByUserId());
        assertEquals(1, emergencyRepository.findAll().stream()
            .filter(e -> shift.getId().equals(e.getShiftId())).count());
    }

    @Test
    void theQueueListsTheDriverPanicFirstAndStillShowsTheAwaitingGroup() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String id = panic();

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.driverEmergencies.length()").value(1))
            .andExpect(jsonPath("$.driverEmergencies[0].id").value(id))
            .andExpect(jsonPath("$.groupsAwaitingApproval.length()").value(1))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].groupId").value(group.getId()));
    }

    @Test
    void anApprovedGroupAndALaterDriverPanicAreIndependentEmergencies() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated());
        String panicId = panic();

        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.APPROVED, stored.getStatus());
        Emergency fromGroup = emergencyRepository.findByGroupId(group.getId()).orElseThrow();
        assertEquals(EmergencyStatus.ACTIVE, fromGroup.getStatus());
        assertEquals(EmergencyStatus.ACTIVE, emergencyRepository.findById(panicId).orElseThrow().getStatus());
        queue().andExpect(jsonPath("$.driverEmergencies[0].id").value(panicId))
            .andExpect(jsonPath("$.passengerEmergencies[0].id").value(fromGroup.getId()));
    }

    // --- US05 S2: closing the shift does not close or reject a safety record ---

    @Test
    void closingTheShiftLeavesAnAwaitingGroupAwaitingAndStillApprovable() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        closeShiftThroughTheEndpoint();

        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.AWAITING_APPROVAL, stored.getStatus());
        assertEquals(1, stored.getOpenMarker());
        queue().andExpect(jsonPath("$.groupsAwaitingApproval[0].groupId").value(group.getId()));
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.priority").value("HIGH"));
        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
    }

    @Test
    void closingTheShiftLeavesAnAwaitingGroupDeclinableToo() throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        closeShiftThroughTheEndpoint();

        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/decline")
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Not credible\"}")
                .with(supervisor()))
            .andExpect(status().isOk());

        assertEquals(GroupStatus.NOT_APPROVED, reload(group).getStatus());
    }

    @Test
    void closingTheShiftKeepsAnApprovedEmergencyActiveAndFollowableUntilItIsClosedByASupervisor()
            throws Exception {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated());
        String emergencyId = emergencyRepository.findByGroupId(group.getId()).orElseThrow().getId();

        closeShiftThroughTheEndpoint();

        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
        assertEquals(EmergencyStatus.ACTIVE, emergencyRepository.findById(emergencyId).orElseThrow().getStatus());
        queue().andExpect(jsonPath("$.passengerEmergencies[0].id").value(emergencyId));
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/start-attention").with(supervisor()))
            .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/emergencies/" + emergencyId + "/close")
                .contentType(MediaType.APPLICATION_JSON).content("{\"outcome\":\"Resolved\"}")
                .with(supervisor()))
            .andExpect(status().isOk());
        assertEquals(GroupStatus.CLOSED, reload(group).getStatus());
    }

    @Test
    void closingTheShiftDoesNotLetTheExpiryJobTouchAnAwaitingGroup() throws Exception {
        PassengerRequestGroup awaiting = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        closeShiftThroughTheEndpoint();
        clock.advance(Duration.ofDays(2));

        int expired = expireOverdueGroups.expire();

        assertEquals(0, expired);
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(awaiting).getStatus());
        assertEquals(1, reload(awaiting).getOpenMarker());
    }

    @Test
    void aDriverPanicWhileAGroupIsCollectingIsCriticalAndLeavesTheGroupAndItsContributorsUntouched()
            throws Exception {
        submitPassengerRequest(76101L);
        submitPassengerRequest(76102L);
        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(GroupStatus.COLLECTING, group.getStatus());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));

        String id = panic();

        Emergency driverEmergency = emergencyRepository.findById(id).orElseThrow();
        assertEquals(EmergencyStatus.ACTIVE, driverEmergency.getStatus());
        assertNull(driverEmergency.getGroupId());
        assertEquals(1, groupsOfShift().size());
        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.COLLECTING, stored.getStatus());
        assertEquals(1, stored.getOpenMarker());
        assertEquals(group.getVersion(), stored.getVersion());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));
        assertEquals(0, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.ATTACHED));
    }

    @Test
    void theExpiryJobNeverTouchesAnApprovedGroupOlderThanTheWindow() {
        PassengerRequestGroup approved = groupInStatus(GroupStatus.APPROVED);
        clock.advance(Duration.ofDays(2));

        int expired = expireOverdueGroups.expire();

        assertEquals(0, expired);
        PassengerRequestGroup stored = reload(approved);
        assertEquals(GroupStatus.APPROVED, stored.getStatus());
        assertEquals(1, stored.getOpenMarker());
    }
}
