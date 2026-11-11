package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PassengerRequestControllerTest extends AbstractPassengerRequestTest {

    private static final String URL = "/api/v1/passenger-requests";
    private static final long U1 = 63001L;
    private static final long U2 = 63002L;
    private static final byte[] PHOTO = TestImages.jpeg(3);

    @Autowired MockMvc mockMvc;

    private static RequestPostProcessor jwtOf(long userId, String role) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", role))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private MockMultipartHttpServletRequestBuilder form(String id, Object journeyId, String message,
                                                        String activatedAt, byte[] photo) {
        MockMultipartHttpServletRequestBuilder request = multipart(URL);
        if (id != null) request.param("id", id);
        if (journeyId != null) request.param("journeyId", String.valueOf(journeyId));
        if (message != null) request.param("message", message);
        if (activatedAt != null) request.param("activatedAt", activatedAt);
        if (photo != null) request.file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", photo));
        return request;
    }

    private MockMultipartHttpServletRequestBuilder validForm(String id, PassengerJourney journey) {
        return form(id, journey.getId(), "Danger on board", clock.instant().minusSeconds(20).toString(), PHOTO);
    }

    private ResultActions send(MockMultipartHttpServletRequestBuilder request, long userId) throws Exception {
        return mockMvc.perform(request.with(jwtOf(userId, "PASSENGER")));
    }

    private void assertRejected(MockMultipartHttpServletRequestBuilder request, long userId, int httpStatus,
                                String code) throws Exception {
        long requests = requestRepository.count();
        long groups = groupsOfShift().size();
        long images = storedImagesAdded();

        send(request, userId).andExpect(status().is(httpStatus)).andExpect(jsonPath("$.code").value(code));

        assertEquals(requests, requestRepository.count());
        assertEquals(groups, groupsOfShift().size());
        assertEquals(images, storedImagesAdded());
    }

    // --- US08 S1 ---

    @Test
    void firstRequestReturns201WithTheCountedRequestAndItsGroup() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();

        send(validForm(id, journey), U1)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requestId").value(id))
            .andExpect(jsonPath("$.status").value("COUNTED"))
            .andExpect(jsonPath("$.receivedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
            .andExpect(jsonPath("$.contributorCount").value(1))
            .andExpect(jsonPath("$.evidenceImageId").doesNotExist())
            .andExpect(jsonPath("$.passengerUserId").doesNotExist());

        PassengerRequest stored = requestRepository.findById(id).orElseThrow();
        assertArrayEquals(PHOTO, storedImageBytes(stored.getEvidenceImageId()));
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void thirdDistinctAccountReturns201WithTheGroupAwaitingApproval() throws Exception {
        for (long user : new long[] {U1, U2}) {
            send(validForm(UUID.randomUUID().toString(), activeJourney(user)), user).andExpect(status().isCreated());
        }

        send(validForm(UUID.randomUUID().toString(), activeJourney(U1 + 100)), U1 + 100)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("COUNTED"))
            .andExpect(jsonPath("$.groupStatus").value("AWAITING_APPROVAL"))
            .andExpect(jsonPath("$.contributorCount").value(3));

        assertEquals(0, emergencyRepository.count());
    }

    // --- idempotency ---

    @Test
    void identicalRetryReturns200WithTheSameBodyAndStoresNothingMore() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        String first = send(validForm(id, journey), U1).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long requests = requestRepository.count();
        long images = storedImagesAdded();

        String retry = send(validForm(id, journey), U1).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        assertEquals(first, retry);
        assertEquals(requests, requestRepository.count());
        assertEquals(images, storedImagesAdded());
    }

    @Test
    void retryAfterAnotherAccountJoinedKeepsTheStoredStatusAndShowsTheNewCount() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        send(validForm(id, journey), U1).andExpect(status().isCreated());
        send(validForm(UUID.randomUUID().toString(), activeJourney(U2)), U2).andExpect(status().isCreated());

        send(validForm(id, journey), U1)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(id))
            .andExpect(jsonPath("$.status").value("COUNTED"))
            .andExpect(jsonPath("$.receivedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.groupStatus").value("COLLECTING"))
            .andExpect(jsonPath("$.contributorCount").value(2));
    }

    @Test
    void anUppercaseIdIsStoredInLowercaseAndTheLowercaseRetryReturns200() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String lower = UUID.randomUUID().toString();

        send(validForm(lower.toUpperCase(), journey), U1)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.requestId").value(lower));
        send(validForm(lower, journey), U1).andExpect(status().isOk());

        assertEquals(1, requestRepository.count());
        assertEquals(lower, requestRepository.findAll().getFirst().getId());
    }

    @Test
    void sameIdWithADifferentPhotoReturns409AndTheStoredDigestStaysTheOriginal() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        send(validForm(id, journey), U1).andExpect(status().isCreated());
        String digest = requestRepository.findById(id).orElseThrow().getEvidenceSha256();

        assertRejected(form(id, journey.getId(), "Danger on board", clock.instant().minusSeconds(20).toString(),
            TestImages.jpeg(99)), U1, 409, "REQUEST_ID_REUSED");

        assertEquals(digest, requestRepository.findById(id).orElseThrow().getEvidenceSha256());
    }

    @Test
    void sameIdWithADifferentMessageOrFromAnotherAccountReturns409() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        PassengerJourney other = activeJourney(U2);
        String id = UUID.randomUUID().toString();
        send(validForm(id, journey), U1).andExpect(status().isCreated());

        assertRejected(form(id, journey.getId(), "Another text", clock.instant().minusSeconds(20).toString(), PHOTO),
            U1, 409, "REQUEST_ID_REUSED");
        assertRejected(validForm(id, other), U2, 409, "REQUEST_ID_REUSED");
    }

    @Test
    void aSecondRequestWithANewIdFromTheSameAccountReturns409AndStoresNothing() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        send(validForm(UUID.randomUUID().toString(), journey), U1).andExpect(status().isCreated());

        assertRejected(validForm(UUID.randomUUID().toString(), journey), U1, 409, "DUPLICATE_CONTRIBUTION");
    }

    // --- US08 S3: rejections, one per code, all leaving no request, group or image ---

    @Test
    void invalidRequestWhenTheJourneyIdOrActivationInstantCannotBeParsed() throws Exception {
        String id = UUID.randomUUID().toString();
        String at = clock.instant().toString();
        assertRejected(form(id, null, "m", at, PHOTO), U1, 422, "INVALID_REQUEST");
        assertRejected(form(id, "abc", "m", at, PHOTO), U1, 422, "INVALID_REQUEST");
        assertRejected(form(id, 1, "m", "yesterday", PHOTO), U1, 422, "INVALID_REQUEST");
    }

    @Test
    void invalidRequestIdWhenMissingOrNotACanonicalUuid() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String at = clock.instant().toString();
        assertRejected(form(null, journey.getId(), "m", at, PHOTO), U1, 422, "INVALID_REQUEST_ID");
        assertRejected(form("1-1-1-1-1", journey.getId(), "m", at, PHOTO), U1, 422, "INVALID_REQUEST_ID");
        assertRejected(form("not-a-uuid", journey.getId(), "m", at, PHOTO), U1, 422, "INVALID_REQUEST_ID");
    }

    @Test
    void messageRequiredAndMessageTooLong() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        String at = clock.instant().toString();
        assertRejected(form(id, journey.getId(), null, at, PHOTO), U1, 422, "MESSAGE_REQUIRED");
        assertRejected(form(id, journey.getId(), "   ", at, PHOTO), U1, 422, "MESSAGE_REQUIRED");
        assertRejected(form(id, journey.getId(), "x".repeat(501), at, PHOTO), U1, 422, "MESSAGE_TOO_LONG");
    }

    @Test
    void evidencePhotoRequiredInvalidAndTooLarge() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        String at = clock.instant().toString();
        assertRejected(form(id, journey.getId(), "m", at, null), U1, 422, "EVIDENCE_PHOTO_REQUIRED");
        assertRejected(form(id, journey.getId(), "m", at, new byte[0]), U1, 422, "EVIDENCE_PHOTO_REQUIRED");
        assertRejected(form(id, journey.getId(), "m", at, TestImages.gif()), U1, 422, "EVIDENCE_PHOTO_INVALID");
        assertRejected(form(id, journey.getId(), "m", at, TestImages.truncatedJpeg()), U1, 422,
            "EVIDENCE_PHOTO_INVALID");
        assertRejected(form(id, journey.getId(), "m", at, TestImages.padded(PHOTO, 5 * 1024 * 1024 + 1)), U1, 422,
            "EVIDENCE_PHOTO_TOO_LARGE");
    }

    @Test
    void invalidActivationTimeWhenMissingOrMoreThanFiveMinutesInTheFuture() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        String id = UUID.randomUUID().toString();
        Instant future = clock.instant().plus(Duration.ofMinutes(5)).plusMillis(1);
        assertRejected(form(id, journey.getId(), "m", null, PHOTO), U1, 422, "INVALID_ACTIVATION_TIME");
        assertRejected(form(id, journey.getId(), "m", future.toString(), PHOTO), U1, 422,
            "INVALID_ACTIVATION_TIME");
    }

    @Test
    void activationAfterTheEndOfTheJourneyIsRejected() throws Exception {
        PassengerJourney ended = endedJourney(U1, clock.instant().minusSeconds(120));
        assertRejected(form(UUID.randomUUID().toString(), ended.getId(), "m",
            clock.instant().minusSeconds(119).toString(), PHOTO), U1, 422, "ACTIVATION_AFTER_JOURNEY_END");
    }

    @Test
    void busNotFoundWhenTheJourneyBusNoLongerExists() throws Exception {
        PassengerJourney orphan = journeyRepository.saveAndFlush(
            PassengerJourney.start(U1, 999_999_999L, shift.getId(), clock));
        try {
            assertRejected(validForm(UUID.randomUUID().toString(), orphan), U1, 422, "BUS_NOT_FOUND");
        } finally {
            journeyRepository.delete(orphan);
        }
    }

    // --- auth ---

    @Test
    void withoutATokenReturns401() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        mockMvc.perform(validForm(UUID.randomUUID().toString(), journey)).andExpect(status().isUnauthorized());
        assertEquals(0, requestRepository.count());
    }

    @Test
    void anotherRoleReturns403AndStoresNothing() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        for (String role : new String[] {"DRIVER", "COMPANY_ADMIN"}) {
            mockMvc.perform(validForm(UUID.randomUUID().toString(), journey).with(jwtOf(U1, role)))
                .andExpect(status().isForbidden());
        }
        assertEquals(0, requestRepository.count());
        assertEquals(0, storedImagesAdded());
    }

    @Test
    void aJourneyOfAnotherAccountReturns403JourneyAccessDenied() throws Exception {
        PassengerJourney others = activeJourney(U2);
        assertRejected(validForm(UUID.randomUUID().toString(), others), U1, 403, "JOURNEY_ACCESS_DENIED");
    }
}
