package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.PassengerAccount;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.safetycase.application.ExpireOverdueGroups;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.domain.model.StoredImage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PassengerRequestWindowControllerTest extends AbstractPassengerRequestTest {

    private static final String URL = "/api/v1/passenger-requests";
    private static final long U1 = 64001L;
    private static final long U2 = 64002L;
    private static final long U3 = 64003L;
    private static final byte[] PHOTO = TestImages.jpeg(5);

    @Autowired MockMvc mockMvc;
    @Autowired ExpireOverdueGroups expireOverdueGroups;
    @Autowired PassengerAccountRepository accountRepository;

    private PassengerAccount account;

    @AfterEach
    void deleteAccount() {
        if (account != null) {
            accountRepository.delete(account);
        }
    }

    private ResultActions send(long userId, PassengerJourney journey, Instant activatedAt) throws Exception {
        return send(userId, journey.getId(), activatedAt, PHOTO);
    }

    private ResultActions send(long userId, Object journeyId, Instant activatedAt, byte[] photo) throws Exception {
        RequestPostProcessor token = jwt()
            .jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
        MockMultipartHttpServletRequestBuilder request = multipart(URL)
            .param("id", UUID.randomUUID().toString())
            .param("journeyId", String.valueOf(journeyId))
            .param("message", "Danger on board")
            .param("activatedAt", activatedAt.toString());
        if (photo != null) {
            request.file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", photo));
        }
        return mockMvc.perform(request.with(token));
    }

    private ResultActions sendNow(long userId, PassengerJourney journey) throws Exception {
        return send(userId, journey, clock.instant().minusSeconds(5));
    }

    // --- US08 S4 ---

    @Test
    void afterTheJobExpiredTheGroupANewRequestStartsANewWindowWithOnlyItselfAsContributor() throws Exception {
        sendNow(U1, activeJourney(U1)).andExpect(status().isCreated());
        sendNow(U2, activeJourney(U2)).andExpect(status().isCreated());
        PassengerJourney third = activeJourney(U3);
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertEquals(1, expireOverdueGroups.expire());

        send(U3, third, clock.instant()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COUNTED"))
            .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
            .andExpect(jsonPath("$.contributorCount").value(1));

        assertEquals(2, groupsOfShift().size());
        PassengerRequestGroup expired = groupsOfShift().get(0);
        PassengerRequestGroup fresh = groupsOfShift().get(1);
        assertEquals(GroupStatus.EXPIRED, expired.getStatus());
        assertNull(expired.getOpenMarker());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(expired.getId(), RequestStatus.COUNTED));
        assertEquals(1, requestRepository.countByGroupIdAndStatus(fresh.getId(), RequestStatus.COUNTED));
        assertEquals(clock.instant(), fresh.getWindowStartedAt());
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void throughTheLazyPathWithNoJobRunTheNewRequestAlsoStartsANewGroup() throws Exception {
        sendNow(U1, activeJourney(U1)).andExpect(status().isCreated());
        sendNow(U2, activeJourney(U2)).andExpect(status().isCreated());
        PassengerJourney third = activeJourney(U3);
        clock.advance(Duration.ofMinutes(5));

        send(U3, third, clock.instant()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
            .andExpect(jsonPath("$.contributorCount").value(1));

        assertEquals(GroupStatus.EXPIRED, groupsOfShift().get(0).getStatus());
        assertEquals(GroupStatus.COLLECTING, groupsOfShift().get(1).getStatus());
        assertEquals(0, emergencyRepository.count());
    }

    // --- US08 S5 ---

    @Test
    void aRequestActivatedTenMinutesAgoIsStoredAsLateWithNoGroupAndBothTimestamps() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        Instant activatedAt = clock.instant().minus(Duration.ofMinutes(10));

        String body = send(U1, journey, activatedAt).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("LATE"))
            .andExpect(jsonPath("$.groupStatus").doesNotExist())
            .andExpect(jsonPath("$.contributorCount").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        assertEquals(0, groupsOfShift().size());
        PassengerRequest stored = requestRepository.findAll().getFirst();
        assertEquals(RequestStatus.LATE, stored.getStatus());
        assertNull(stored.getGroupId());
        assertEquals(activatedAt, stored.getActivatedAt());
        assertEquals(clock.instant(), stored.getReceivedAt());
        assertTrue(body.contains(clock.instant().toString()));
    }

    @Test
    void exactlyFiveMinutesOldIsEligibleAndOneMillisecondOlderIsLate() throws Exception {
        send(U1, activeJourney(U1), clock.instant().minus(Duration.ofMinutes(5)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("COUNTED"));

        send(U2, activeJourney(U2), clock.instant().minus(Duration.ofMinutes(5)).minusMillis(1))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("LATE"));

        assertEquals(1, requestRepository.countByGroupIdAndStatus(groupsOfShift().getFirst().getId(),
            RequestStatus.COUNTED));
    }

    @Test
    void aRequestForAnEndedJourneyActivatedBeforeItsEndIsStoredAsLate() throws Exception {
        PassengerJourney ended = endedJourney(U1, clock.instant().minusSeconds(60));

        send(U1, ended, clock.instant().minusSeconds(90)).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("LATE"))
            .andExpect(jsonPath("$.groupStatus").doesNotExist());

        assertEquals(0, groupsOfShift().size());
    }

    @Test
    void aLateRequestDoesNotContributeToTheThresholdOfAnOpenGroup() throws Exception {
        sendNow(U1, activeJourney(U1)).andExpect(status().isCreated());
        sendNow(U2, activeJourney(U2)).andExpect(status().isCreated());

        send(U3, activeJourney(U3), clock.instant().minus(Duration.ofMinutes(10))).andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("LATE"));

        assertEquals(GroupStatus.COLLECTING, groupsOfShift().getFirst().getStatus());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(groupsOfShift().getFirst().getId(),
            RequestStatus.COUNTED));
    }

    // --- US08 S6 ---

    @Test
    void anEligibleRequestForAnAwaitingOrApprovedGroupIsAttachedAndTheGroupIsUnchanged() throws Exception {
        long user = U1;
        for (GroupStatus status : new GroupStatus[] {GroupStatus.AWAITING_APPROVAL, GroupStatus.APPROVED}) {
            PassengerRequestGroup group = groupInStatus(status);
            Long version = group.getVersion();

            sendNow(user, activeJourney(user)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ATTACHED"))
                .andExpect(jsonPath("$.groupStatus").value(status.name()));

            assertEquals(1, groupsOfShift().size());
            PassengerRequestGroup after = groupRepository.findById(group.getId()).orElseThrow();
            assertEquals(status, after.getStatus());
            assertEquals(version, after.getVersion());
            assertEquals(0, emergencyRepository.count());

            requestRepository.deleteAll();
            groupRepository.delete(after);
            user = U2;
        }
    }

    @Test
    void afterANotApprovedOrClosedGroupAnEligibleRequestStartsANewGroup() throws Exception {
        long user = U1;
        for (GroupStatus status : new GroupStatus[] {GroupStatus.NOT_APPROVED, GroupStatus.CLOSED}) {
            PassengerRequestGroup old = groupInStatus(status);

            sendNow(user, activeJourney(user)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("COUNTED"))
                .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
                .andExpect(jsonPath("$.contributorCount").value(1));

            assertEquals(2, groupsOfShift().size());
            assertEquals(status, groupRepository.findById(old.getId()).orElseThrow().getStatus());

            requestRepository.deleteAll();
            groupRepository.deleteAll(groupsOfShift());
            user = U2;
        }
    }

    // --- US08 S3 / US23 S4: the registration face photo is never evidence ---

    @Test
    void theRegistrationFacePhotoIsNeverUsedAsIncidentEvidence() throws Exception {
        byte[] facePhoto = TestImages.png(42);
        Long faceImageId = storedImageRepository.save(StoredImage.create(facePhoto, "image/png", clock)).getId();
        account = accountRepository.save(PassengerAccount.register(U1, "12345678", faceImageId, "2026-10", clock));
        PassengerJourney journey = activeJourney(U1);

        send(U1, journey.getId(), clock.instant(), null).andExpect(status().isUnprocessableContent())
            .andExpect(jsonPath("$.code").value("EVIDENCE_PHOTO_REQUIRED"));
        assertEquals(0, requestRepository.count());

        String body = send(U1, journey, clock.instant()).andExpect(status().isCreated())
            .andExpect(jsonPath("$.faceImageId").doesNotExist())
            .andExpect(jsonPath("$.evidenceImageId").doesNotExist())
            .andReturn().getResponse().getContentAsString();

        PassengerRequest stored = requestRepository.findAll().getFirst();
        assertNotEquals(account.getFaceImageId(), stored.getEvidenceImageId());
        assertArrayEquals(PHOTO, storedImageBytes(stored.getEvidenceImageId()));
        assertArrayEquals(facePhoto, storedImageBytes(account.getFaceImageId()));
        assertFalse(body.toLowerCase().contains("image"));
    }

    // --- US08 S3: an unknown journey and another account's journey are indistinguishable ---

    @Test
    void anUnknownJourneyAndAnotherAccountsJourneyGiveTheSameStatusCodeAndDetail() throws Exception {
        PassengerJourney others = activeJourney(U2);

        String foreign = send(U1, others, clock.instant()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("JOURNEY_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String unknown = send(U1, 999_999_999L, clock.instant(), PHOTO).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("JOURNEY_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(foreign, unknown);
        assertEquals(0, requestRepository.count());
    }
}
