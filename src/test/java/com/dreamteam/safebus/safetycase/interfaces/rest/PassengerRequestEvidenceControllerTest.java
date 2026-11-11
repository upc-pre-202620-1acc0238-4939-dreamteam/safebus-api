package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.PassengerAccount;
import com.dreamteam.safebus.passenger.domain.repository.PassengerAccountRepository;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PassengerRequestEvidenceControllerTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 75001L;
    private static final long U1 = 75101L;
    private static final long U2 = 75102L;
    private static final long U3 = 75103L;
    private static final byte[] FACE = TestImages.png(77);

    @Autowired MockMvc mockMvc;
    @Autowired PassengerAccountRepository accountRepository;

    private PassengerAccount account;

    @AfterEach
    void deleteAccount() {
        if (account != null) {
            accountRepository.delete(account);
        }
    }

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

    private String submit(long userId, byte[] photo, java.time.Instant activatedAt) throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", photo))
                .param("id", id)
                .param("journeyId", String.valueOf(activeJourney(userId).getId()))
                .param("message", "Danger on board")
                .param("activatedAt", activatedAt.toString())
                .with(token(userId, "PASSENGER", null)))
            .andExpect(status().isCreated());
        return id;
    }

    private String submit(long userId, byte[] photo) throws Exception {
        return submit(userId, photo, clock.instant().minusSeconds(5));
    }

    private ResultActions photo(String requestId, RequestPostProcessor auth) throws Exception {
        return mockMvc.perform(get("/api/v1/passenger-requests/" + requestId + "/photo").with(auth));
    }

    // --- US17 S1 / S5: evidence for a supervisor of the same company ---

    @Test
    void aSupervisorOfTheCompanyReceivesTheStoredPhotoWithProtectiveHeaders() throws Exception {
        byte[] photo = TestImages.jpeg(11);
        String id = submit(U1, photo);

        MvcResult result = photo(id, supervisor()).andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();

        assertArrayEquals(photo, result.getResponse().getContentAsByteArray());
    }

    @Test
    void thePhotoIsServedForEveryRequestOfAnAwaitingGroup() throws Exception {
        String first = submit(U1, TestImages.jpeg(12));
        submit(U2, TestImages.jpeg(13));
        String third = submit(U3, TestImages.jpeg(14));

        photo(first, supervisor()).andExpect(status().isOk());
        photo(third, supervisor()).andExpect(status().isOk());
    }

    @Test
    void theResponseNeverContainsTheRegistrationPhotoOrItsId() throws Exception {
        Long faceImageId = storedImageRepository.save(StoredImage.create(FACE, "image/png", clock)).getId();
        account = accountRepository.save(PassengerAccount.register(U1, "12345678", faceImageId, "2026-10", clock));
        byte[] evidence = TestImages.jpeg(15);
        String id = submit(U1, evidence);

        MvcResult result = photo(id, supervisor()).andExpect(status().isOk()).andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertArrayEquals(evidence, body);
        assertFalse(java.util.Arrays.equals(FACE, body));
        String headers = result.getResponse().getHeaderNames().stream()
            .map(n -> n + "=" + result.getResponse().getHeaders(n)).toList().toString();
        assertFalse(headers.contains("faceImage"));
        assertFalse(headers.contains(String.valueOf(faceImageId)));
    }

    @Test
    void theRegistrationPhotoCannotBeReachedThroughThisEndpoint() throws Exception {
        Long faceImageId = storedImageRepository.save(StoredImage.create(FACE, "image/png", clock)).getId();
        account = accountRepository.save(PassengerAccount.register(U1, "12345678", faceImageId, "2026-10", clock));
        submit(U1, TestImages.jpeg(16));

        photo(String.valueOf(faceImageId), supervisor()).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EVIDENCE_ACCESS_DENIED"));
    }

    @Test
    void aForeignMissingMalformedAndLateRequestAllGiveTheSame403() throws Exception {
        String id = submit(U1, TestImages.jpeg(17));
        String late = submit(U2, TestImages.jpeg(18), clock.instant().minus(Duration.ofMinutes(10)));
        RequestPostProcessor foreign = token(SUPERVISOR, "SUPERVISOR", company.getId() + 1_000_000);

        String foreignBody = photo(id, foreign).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("EVIDENCE_ACCESS_DENIED")).andReturn().getResponse().getContentAsString();
        String missingBody = photo(UUID.randomUUID().toString(), supervisor()).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String malformedBody = photo("not-a-uuid", supervisor()).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String lateBody = photo(late, supervisor()).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(withoutInstance(foreignBody), withoutInstance(missingBody));
        assertEquals(withoutInstance(foreignBody), withoutInstance(malformedBody));
        assertEquals(withoutInstance(foreignBody), withoutInstance(lateBody));
    }

    @Test
    void passengersDriversAndTokensWithoutCompanyAreRefusedAndNoTokenIs401() throws Exception {
        String id = submit(U1, TestImages.jpeg(19));

        photo(id, token(U1, "PASSENGER", null)).andExpect(status().isForbidden());
        photo(id, token(1L, "DRIVER", company.getId())).andExpect(status().isForbidden());
        photo(id, token(SUPERVISOR, "SUPERVISOR", null)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/passenger-requests/" + id + "/photo")).andExpect(status().isUnauthorized());
    }

    @Test
    void aRequestWhoseImageRowIsGoneIs404() throws Exception {
        String id = submit(U1, TestImages.jpeg(20));
        Long imageId = requestRepository.findById(id).map(PassengerRequest::getEvidenceImageId).orElseThrow();
        jdbcTemplate.update("delete from stored_image where id = ?", imageId);

        photo(id, supervisor()).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    private static String withoutInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }
}
