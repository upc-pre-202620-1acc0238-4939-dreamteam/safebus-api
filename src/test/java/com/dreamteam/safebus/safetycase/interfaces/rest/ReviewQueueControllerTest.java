package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReviewQueueControllerTest extends AbstractPassengerRequestTest {

    private static final String URL = "/api/v1/review-queue";
    private static final long SUPERVISOR = 74001L;
    private static final long U1 = 74101L;
    private static final long U2 = 74102L;
    private static final long U3 = 74103L;
    private static final long U4 = 74104L;

    @Autowired MockMvc mockMvc;

    private final List<Long> extraGroupIds = new ArrayList<>();

    @AfterEach
    void deleteExtraGroups() {
        groupRepository.deleteAllById(extraGroupIds);
    }

    private RequestPostProcessor token(String role, Long companyId) {
        return jwt().jwt(b -> {
            b.subject(String.valueOf(SUPERVISOR)).claim("role", role);
            if (companyId != null) {
                b.claim("companyId", companyId);
            }
        }).authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions queue() throws Exception {
        return mockMvc.perform(get(URL).with(token("SUPERVISOR", company.getId())));
    }

    private String queueJson() throws Exception {
        return queue().andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private Emergency driverEmergency(String id, Long companyId) {
        return emergencyRepository.save(Emergency.activateByDriver(id, companyId, driver.getId(), bus.getId(),
            shift.getId(), route.getId(), null, clock.instant().minusSeconds(5), clock));
    }

    private PassengerRequestGroup extraAwaitingGroup(long shiftId, java.time.Instant awaitingSince) {
        PassengerRequestGroup group = PassengerRequestGroup.open(company.getId(), bus.getId(), shiftId,
            clock.instant());
        ReflectionTestUtils.setField(group, "status", GroupStatus.AWAITING_APPROVAL);
        ReflectionTestUtils.setField(group, "awaitingSince", awaitingSince);
        group = groupRepository.saveAndFlush(group);
        extraGroupIds.add(group.getId());
        return group;
    }

    private int submitted;

    private void submitRequest(long userId) throws Exception {
        clock.advance(Duration.ofSeconds(1));
        int sequence = ++submitted;
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(3)))
                .param("id", UUID.randomUUID().toString())
                .param("journeyId", String.valueOf(activeJourney(userId).getId()))
                .param("message", "Message " + sequence)
                .param("activatedAt", clock.instant().minusSeconds(5).toString())
                .with(jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
                    .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"))))
            .andExpect(status().isCreated());
    }

    // --- US10 S5: preserve the agreed priority ---

    @Test
    void anEmptyCompanyGetsThreeEmptyBlocks() throws Exception {
        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.generatedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.driverEmergencies").isEmpty())
            .andExpect(jsonPath("$.passengerEmergencies").isEmpty())
            .andExpect(jsonPath("$.groupsAwaitingApproval").isEmpty());
    }

    @Test
    void driverEmergenciesPassengerEmergenciesAndAwaitingGroupsAreListedInSeparateBlocks() throws Exception {
        PassengerRequestGroup approved = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        mockMvc.perform(post("/api/v1/request-groups/" + approved.getId() + "/approve")
            .with(token("SUPERVISOR", company.getId()))).andExpect(status().isCreated());
        clock.advance(Duration.ofSeconds(10));
        driverEmergency("drv-1", company.getId());
        PassengerRequestGroup awaiting = extraAwaitingGroup(shift.getId() + 1_000_000, clock.instant());

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.driverEmergencies.length()").value(1))
            .andExpect(jsonPath("$.driverEmergencies[0].id").value("drv-1"))
            .andExpect(jsonPath("$.driverEmergencies[0].priority").value("CRITICAL"))
            .andExpect(jsonPath("$.driverEmergencies[0].groupId").doesNotExist())
            .andExpect(jsonPath("$.passengerEmergencies.length()").value(1))
            .andExpect(jsonPath("$.passengerEmergencies[0].priority").value("HIGH"))
            .andExpect(jsonPath("$.passengerEmergencies[0].groupId").value(approved.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval.length()").value(1))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].groupId").value(awaiting.getId()));
    }

    @Test
    void withinABlockActiveComesBeforeInProgressThenOldestFirstThenId() throws Exception {
        driverEmergency("drv-b", company.getId());
        driverEmergency("drv-a", company.getId());
        clock.advance(Duration.ofSeconds(1));
        driverEmergency("drv-older-in-progress", company.getId());
        clock.advance(Duration.ofSeconds(1));
        driverEmergency("drv-newest", company.getId());
        Emergency inProgress = emergencyRepository.findById("drv-older-in-progress").orElseThrow();
        inProgress.startAttention(SUPERVISOR, clock.instant());
        emergencyRepository.save(inProgress);

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.driverEmergencies[0].id").value("drv-a"))
            .andExpect(jsonPath("$.driverEmergencies[1].id").value("drv-b"))
            .andExpect(jsonPath("$.driverEmergencies[2].id").value("drv-newest"))
            .andExpect(jsonPath("$.driverEmergencies[3].id").value("drv-older-in-progress"))
            .andExpect(jsonPath("$.driverEmergencies[3].status").value("IN_PROGRESS"));
    }

    @Test
    void groupsAreOrderedByAwaitingSinceThenId() throws Exception {
        PassengerRequestGroup late = extraAwaitingGroup(shift.getId() + 1_000_001, clock.instant().plusSeconds(60));
        PassengerRequestGroup tieFirst = extraAwaitingGroup(shift.getId() + 1_000_002, clock.instant());
        PassengerRequestGroup tieSecond = extraAwaitingGroup(shift.getId() + 1_000_003, clock.instant());

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].groupId").value(tieFirst.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval[1].groupId").value(tieSecond.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval[2].groupId").value(late.getId()));
    }

    @Test
    void theQueueIsDeterministicAcrossCalls() throws Exception {
        driverEmergency("drv-x", company.getId());
        driverEmergency("drv-y", company.getId());
        extraAwaitingGroup(shift.getId() + 1_000_004, clock.instant());
        extraAwaitingGroup(shift.getId() + 1_000_005, clock.instant());

        assertEquals(queueJson(), queueJson());
    }

    @Test
    void onlyOpenWorkOfTheCallersCompanyIsListed() throws Exception {
        driverEmergency("drv-foreign", company.getId() + 1_000_000);
        driverEmergency("drv-closed", company.getId());
        Emergency closed = emergencyRepository.findById("drv-closed").orElseThrow();
        closed.startAttention(SUPERVISOR, clock.instant());
        closed.close("done", null, clock.instant());
        emergencyRepository.save(closed);
        PassengerRequestGroup foreignGroup = PassengerRequestGroup.open(company.getId() + 1_000_000, bus.getId(),
            shift.getId() + 1_000_010, clock.instant());
        ReflectionTestUtils.setField(foreignGroup, "status", GroupStatus.AWAITING_APPROVAL);
        ReflectionTestUtils.setField(foreignGroup, "awaitingSince", clock.instant());
        extraGroupIds.add(groupRepository.saveAndFlush(foreignGroup).getId());
        for (GroupStatus status : List.of(GroupStatus.COLLECTING, GroupStatus.NOT_APPROVED)) {
            PassengerRequestGroup g = groupInStatus(status);
            if (status == GroupStatus.COLLECTING) {
                groupRepository.delete(g);
            }
        }

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.driverEmergencies").isEmpty())
            .andExpect(jsonPath("$.passengerEmergencies").isEmpty())
            .andExpect(jsonPath("$.groupsAwaitingApproval").isEmpty());
    }

    @Test
    void anAwaitingGroupListsItsRequestsAndCountsContributorsApartFromAttachedOnes() throws Exception {
        submitRequest(U1);
        submitRequest(U2);
        submitRequest(U3);
        submitRequest(U4);
        PassengerRequestGroup group = groupsOfShift().getFirst();

        String json = queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].groupId").value(group.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].busId").value(bus.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].shiftId").value(shift.getId()))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].contributorCount").value(3))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].requestCount").value(4))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].requests.length()").value(4))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].requests[0].message").value("Message 1"))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].requests[0].status").value("COUNTED"))
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].requests[3].status").value("ATTACHED"))
            .andReturn().getResponse().getContentAsString();

        String firstId = com.jayway.jsonpath.JsonPath.read(json, "$.groupsAwaitingApproval[0].requests[0].requestId");
        assertEquals("/api/v1/passenger-requests/" + firstId + "/photo",
            com.jayway.jsonpath.JsonPath.read(json, "$.groupsAwaitingApproval[0].requests[0].photoPath"));
    }

    @Test
    void aGroupReferenceIsItsStoredPublicReference() throws Exception {
        submitRequest(U1);
        submitRequest(U2);
        submitRequest(U3);
        PassengerRequestGroup group = groupsOfShift().getFirst();

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].reference").value(group.getPublicReference()));
    }

    @Test
    void aLegacyGroupWithoutStoredReferenceGetsTheDerivedStableReference() throws Exception {
        PassengerRequestGroup legacy = extraAwaitingGroup(shift.getId(), clock.instant());
        jdbcTemplate.update("update passenger_request_group set public_reference = null where id = ?",
            legacy.getId());
        String expected = com.dreamteam.safebus.safetycase.application.PublicReference
            .groupRecordId(legacy.getId(), null);

        queue().andExpect(status().isOk())
            .andExpect(jsonPath("$.groupsAwaitingApproval[0].reference").value(expected));
        queue().andExpect(jsonPath("$.groupsAwaitingApproval[0].reference").value(expected));
    }

    @Test
    void theQueueNeverExposesPassengerIdentityOrImageData() throws Exception {
        submitRequest(U1);
        submitRequest(U2);
        submitRequest(U3);

        String json = queueJson();

        for (String forbidden : List.of("passengerUserId", "faceImage", "evidenceImageId", "evidenceSha256",
                "sha256", "dni", "password", String.valueOf(U1), String.valueOf(U2), String.valueOf(U3))) {
            assertFalse(json.contains(forbidden), "queue must not contain " + forbidden);
        }
    }

    @Test
    void approvedGroupMovesFromTheAwaitingBlockToThePassengerEmergencyBlock() throws Exception {
        submitRequest(U1);
        submitRequest(U2);
        submitRequest(U3);
        PassengerRequestGroup group = groupsOfShift().getFirst();
        queue().andExpect(jsonPath("$.groupsAwaitingApproval.length()").value(1))
            .andExpect(jsonPath("$.passengerEmergencies").isEmpty());

        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve")
            .with(token("SUPERVISOR", company.getId()))).andExpect(status().isCreated());

        queue().andExpect(jsonPath("$.groupsAwaitingApproval").isEmpty())
            .andExpect(jsonPath("$.passengerEmergencies.length()").value(1))
            .andExpect(jsonPath("$.passengerEmergencies[0].groupId").value(group.getId()));
    }

    @Test
    void onlyASupervisorWithACompanyMayReadTheQueue() throws Exception {
        mockMvc.perform(get(URL).with(token("PASSENGER", null))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(token("DRIVER", company.getId()))).andExpect(status().isForbidden());
        mockMvc.perform(get(URL).with(token("SUPERVISOR", null))).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("REVIEW_ACCESS_DENIED"));
        mockMvc.perform(get(URL).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }
}
