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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;
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
class MyPassengerRequestPhotoTest extends AbstractPassengerRequestTest {

    private static final String MINE = "/api/v1/passenger-requests/mine";
    private static final long SUPERVISOR = 79001L;
    private static final long U1 = 79101L;
    private static final long U2 = 79102L;

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

    private String submit(long userId, PassengerJourney journey, byte[] photo, Instant activatedAt)
        throws Exception {
        String id = UUID.randomUUID().toString();
        mockMvc.perform(multipart("/api/v1/passenger-requests")
                .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", photo))
                .param("id", id)
                .param("journeyId", String.valueOf(journey.getId()))
                .param("message", "Danger on board")
                .param("activatedAt", activatedAt.toString())
                .with(passenger(userId)))
            .andExpect(status().isCreated());
        return id;
    }

    private String submit(long userId, byte[] photo) throws Exception {
        return submit(userId, activeJourney(userId), photo, clock.instant().minusSeconds(5));
    }

    private ResultActions photo(long userId, String id) throws Exception {
        return mockMvc.perform(get(MINE + "/" + id + "/photo").with(passenger(userId)));
    }

    private static String withoutInstance(String problem) {
        return problem.replaceAll("\"instance\":\"[^\"]*\",?", "");
    }

    @Test
    void theOwnerReceivesTheStoredPhotoWithProtectiveHeaders() throws Exception {
        byte[] photo = TestImages.jpeg(31);
        String id = submit(U1, photo);

        MvcResult result = photo(U1, id).andExpect(status().isOk())
            .andExpect(content().contentType("image/jpeg"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();

        assertArrayEquals(photo, result.getResponse().getContentAsByteArray());
    }

    @Test
    void theEvidenceReferenceOfTheRequestPointsAtTheOwnersPhoto() throws Exception {
        byte[] photo = TestImages.jpeg(32);
        String id = submit(U1, photo);

        String reference = com.jayway.jsonpath.JsonPath.read(
            mockMvc.perform(get(MINE + "/" + id).with(passenger(U1))).andReturn().getResponse()
                .getContentAsString(), "$.evidenceReference");
        MvcResult result = mockMvc.perform(get(reference).with(passenger(U1))).andExpect(status().isOk())
            .andReturn();

        assertArrayEquals(photo, result.getResponse().getContentAsByteArray());
    }

    @Test
    void theOwnerStillReceivesThePhotoOfALateRequestAfterTheJourneyEnded() throws Exception {
        byte[] photo = TestImages.jpeg(33);
        Instant endedAt = clock.instant().plusSeconds(1);
        PassengerJourney ended = endedJourney(U1, endedAt);
        clock.advance(Duration.ofMinutes(1));
        String late = submit(U1, ended, photo, endedAt.minusSeconds(10));

        MvcResult result = photo(U1, late).andExpect(status().isOk()).andReturn();

        assertArrayEquals(photo, result.getResponse().getContentAsByteArray());
    }

    @Test
    void anotherAccountsMissingAndMalformedIdsGiveTheIdentical403() throws Exception {
        String others = submit(U2, TestImages.jpeg(34));

        String foreign = photo(U1, others).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSENGER_REQUEST_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String missing = photo(U1, UUID.randomUUID().toString()).andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String malformed = photo(U1, "not-a-uuid").andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();
        String numeric = photo(U1, "42").andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        assertEquals(withoutInstance(foreign), withoutInstance(missing));
        assertEquals(withoutInstance(foreign), withoutInstance(malformed));
        assertEquals(withoutInstance(foreign), withoutInstance(numeric));
        assertFalse(withoutInstance(foreign).contains(others));
    }

    @Test
    void aMissingImageIs404OnlyForTheOwnerAndStillForbiddenForEveryoneElse() throws Exception {
        String id = submit(U2, TestImages.jpeg(35));
        Long imageId = requestRepository.findById(id).map(PassengerRequest::getEvidenceImageId).orElseThrow();
        jdbcTemplate.update("delete from stored_image where id = ?", imageId);

        photo(U1, id).andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSENGER_REQUEST_ACCESS_DENIED"));
        photo(U2, id).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    @Test
    void onlyPassengersAreServedAndAnonymousCallersGet401() throws Exception {
        String id = submit(U1, TestImages.jpeg(36));

        mockMvc.perform(get(MINE + "/" + id + "/photo")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(MINE + "/" + id + "/photo").with(supervisor())).andExpect(status().isForbidden())
            .andExpect(content().string(""));
    }

    @Test
    void theSupervisorEndpointIsUnchangedAndStaysClosedToThePassenger() throws Exception {
        byte[] photo = TestImages.jpeg(37);
        String id = submit(U1, photo);
        submit(U2, TestImages.jpeg(38));
        submit(79103L, TestImages.jpeg(39));

        MvcResult result = mockMvc.perform(get("/api/v1/passenger-requests/" + id + "/photo").with(supervisor()))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andReturn();
        assertArrayEquals(photo, result.getResponse().getContentAsByteArray());

        mockMvc.perform(get("/api/v1/passenger-requests/" + id + "/photo").with(passenger(U1)))
            .andExpect(status().isForbidden()).andExpect(content().string(""));
    }

    @Test
    void anIdThatIsLiterallyPhotoIsAnsweredWithTheCommonPassengerDenial() throws Exception {
        submit(U1, TestImages.jpeg(40));

        String body = mockMvc.perform(get(MINE + "/photo").with(passenger(U1)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("PASSENGER_REQUEST_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();

        assertEquals(withoutInstance(body), withoutInstance(
            photo(U1, "not-a-uuid").andReturn().getResponse().getContentAsString()));
    }
}
