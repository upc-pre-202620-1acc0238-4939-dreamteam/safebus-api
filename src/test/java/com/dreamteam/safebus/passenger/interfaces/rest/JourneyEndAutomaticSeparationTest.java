package com.dreamteam.safebus.passenger.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JourneyEndAutomaticSeparationTest extends AbstractPassengerRequestTest {

    private static final long USER = 64001L;
    private static final long OTHER = 64002L;

    @Autowired MockMvc mockMvc;

    private static RequestPostProcessor passengerJwt(long userId) {
        return jwt().jwt(b -> b.subject(String.valueOf(userId)).claim("role", "PASSENGER"))
            .authorities(new SimpleGrantedAuthority("ROLE_PASSENGER"));
    }

    private ResultActions end(Object journeyId, long userId, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/journeys/" + journeyId + "/end")
            .with(passengerJwt(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private PassengerJourney reload(PassengerJourney journey) {
        return journeyRepository.findById(journey.getId()).orElseThrow();
    }

    private String json(String reason, Instant endedAt, Integer distance, Integer duration) {
        StringBuilder sb = new StringBuilder("{\"reason\":\"" + reason + "\"");
        if (endedAt != null) sb.append(",\"endedAt\":\"").append(endedAt).append("\"");
        if (distance != null || duration != null) {
            sb.append(",\"summary\":{");
            if (distance != null) sb.append("\"separationDistanceMeters\":").append(distance);
            if (distance != null && duration != null) sb.append(",");
            if (duration != null) sb.append("\"separationDurationSeconds\":").append(duration);
            sb.append("}");
        }
        return sb.append("}").toString();
    }

    @Test
    void automaticSeparationStoresTheOriginalTimeAndTheSummary() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        Instant original = clock.instant().plus(Duration.ofMinutes(20));
        clock.advance(Duration.ofMinutes(30));

        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", original, 130, 65))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journey.getId()))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endedAt").value(original.toString()))
            .andExpect(jsonPath("$.endReason").value("AUTOMATIC_SEPARATION"))
            .andExpect(jsonPath("$.summary.separationDistanceMeters").value(130))
            .andExpect(jsonPath("$.summary.separationDurationSeconds").value(65));

        PassengerJourney stored = reload(journey);
        assertEquals(JourneyStatus.ENDED, stored.getStatus());
        assertEquals(original, stored.getEndedAt());
        assertEquals(130, stored.getSeparationSummary().distanceMeters());
        assertEquals(65, stored.getSeparationSummary().durationSeconds());
    }

    @Test
    void automaticSeparationWithoutTimeOrSummaryUsesTheServerTimeAndNoSummary() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(10));

        end(journey.getId(), USER, "{\"reason\":\"AUTOMATIC_SEPARATION\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.summary").doesNotExist());
        assertNull(reload(journey).getSeparationSummary());
    }

    @Test
    void theExistingResponseContractIsKeptForManualEnds() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(1));

        end(journey.getId(), USER, "{\"reason\":\"MANUAL\"}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journey.getId()))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endedAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.endReason").value("MANUAL"));
    }

    @Test
    void endedAtBeforeTheStartIsRejectedAndTheJourneyStaysActive() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(10));

        end(journey.getId(), USER, json("MANUAL", journey.getStartedAt().minusSeconds(1), null, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_TIME"));
        assertEquals(JourneyStatus.ACTIVE, reload(journey).getStatus());
    }

    @Test
    void endedAtBeyondTheFutureSkewIsRejected() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(10));

        end(journey.getId(), USER, json("MANUAL", clock.instant().plusSeconds(301), null, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_TIME"));
        assertEquals(JourneyStatus.ACTIVE, reload(journey).getStatus());
    }

    @Test
    void endedAtWithinTheFutureSkewIsClampedToTheServerTime() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(10));

        end(journey.getId(), USER, json("MANUAL", clock.instant().plusSeconds(300), null, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedAt").value(clock.instant().toString()));
    }

    @Test
    void aSummaryWithAnotherReasonOrIncompleteOrNonPositiveIsRejected() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        clock.advance(Duration.ofMinutes(10));

        end(journey.getId(), USER, json("MANUAL", null, 130, 65))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_SUMMARY"));
        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", null, 130, null))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_SUMMARY"));
        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", null, 0, 65))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_SUMMARY"));
        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", null, 130, 601))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_END_SUMMARY"));
        assertEquals(JourneyStatus.ACTIVE, reload(journey).getStatus());
    }

    @Test
    void aReplayWithDifferentReasonTimeAndSummaryReturnsTheOriginalDataAndChangesNothing() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        Instant original = clock.instant().plus(Duration.ofMinutes(20));
        clock.advance(Duration.ofMinutes(30));
        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", original, 130, 65)).andExpect(status().isOk());
        PassengerJourney before = reload(journey);

        clock.advance(Duration.ofMinutes(5));
        end(journey.getId(), USER, json("AUTOMATIC_SEPARATION", original.plusSeconds(120), 500, 100))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedAt").value(original.toString()))
            .andExpect(jsonPath("$.endReason").value("AUTOMATIC_SEPARATION"))
            .andExpect(jsonPath("$.summary.separationDistanceMeters").value(130))
            .andExpect(jsonPath("$.summary.separationDurationSeconds").value(65));
        end(journey.getId(), USER, json("SIGN_OUT", journey.getStartedAt().minusSeconds(60), null, null))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.endedAt").value(original.toString()))
            .andExpect(jsonPath("$.endReason").value("AUTOMATIC_SEPARATION"))
            .andExpect(jsonPath("$.summary.separationDistanceMeters").value(130));

        PassengerJourney after = reload(journey);
        assertEquals(before.getEndedAt(), after.getEndedAt());
        assertEquals(JourneyEndReason.AUTOMATIC_SEPARATION, after.getEndReason());
        assertEquals(before.getSeparationSummary(), after.getSeparationSummary());
    }

    @Test
    void foreignUnknownAndMalformedIdsReturnTheIdentical403() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        String foreign = end(journey.getId(), OTHER, "{}")
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("JOURNEY_ACCESS_DENIED"))
            .andReturn().getResponse().getContentAsString();
        String unknown = end(987654321L, USER, "{}")
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
        String malformed = end("not-a-number", USER, "{}")
            .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();

        assertEquals(strip(foreign), strip(unknown));
        assertEquals(strip(foreign), strip(malformed));
        assertEquals(JourneyStatus.ACTIVE, reload(journey).getStatus());
    }

    @Test
    void anotherRoleIsForbiddenAndNoTokenIsUnauthorized() throws Exception {
        PassengerJourney journey = activeJourney(USER);
        mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
                .with(jwt().jwt(b -> b.subject("1").claim("role", "SUPERVISOR"))
                    .authorities(new SimpleGrantedAuthority("ROLE_SUPERVISOR")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().string(""));
    }

    private static String strip(String problemJson) {
        return problemJson.replaceAll("\"instance\":\"[^\"]*\"", "");
    }
}
