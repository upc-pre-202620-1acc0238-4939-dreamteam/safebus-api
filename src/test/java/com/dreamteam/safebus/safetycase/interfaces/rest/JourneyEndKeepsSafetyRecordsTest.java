package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.ShiftAssignment;
import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JourneyEndKeepsSafetyRecordsTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 76101L;
    private static final long U1 = 77001L;
    private static final long U2 = 77002L;
    private static final long U3 = 77003L;
    private static final long U4 = 77004L;

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

    private RequestPostProcessor driverToken() {
        return jwt().jwt(b -> b.subject(String.valueOf(driver.getUserAccountId())).claim("role", "DRIVER")
                .claim("companyId", company.getId()))
            .authorities(new SimpleGrantedAuthority("ROLE_DRIVER"));
    }

    private ResultActions submit(long userId, PassengerJourney journey, String id, Instant activatedAt)
            throws Exception {
        return mockMvc.perform(multipart("/api/v1/passenger-requests")
            .file(new MockMultipartFile("photo", "evidence.jpg", "image/jpeg", TestImages.jpeg(5)))
            .param("id", id)
            .param("journeyId", String.valueOf(journey.getId()))
            .param("message", "Danger on board")
            .param("activatedAt", activatedAt.toString())
            .with(passenger(userId)));
    }

    private ResultActions end(long userId, PassengerJourney journey, String body) throws Exception {
        return mockMvc.perform(post("/api/v1/journeys/" + journey.getId() + "/end")
            .with(passenger(userId)).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void closeShift() throws Exception {
        ShiftAssignment active = assignmentRepository.findById(shift.getAssignmentId()).orElseThrow();
        active.activate();
        assignmentRepository.saveAndFlush(active);
        mockMvc.perform(post("/api/v1/shifts/" + shift.getId() + "/close").with(driverToken()))
            .andExpect(status().isOk());
    }

    private void panic() throws Exception {
        mockMvc.perform(post("/api/v1/driver-emergencies")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"id\":\"" + UUID.randomUUID() + "\",\"shiftId\":" + shift.getId()
                    + ",\"activatedAt\":\"" + clock.instant().minusSeconds(2) + "\"}")
                .with(driverToken()))
            .andExpect(status().isCreated());
    }

    private PassengerJourney reload(PassengerJourney journey) {
        return journeyRepository.findById(journey.getId()).orElseThrow();
    }

    private List<String> snapshot() {
        List<String> rows = new ArrayList<>();
        requestRepository.findAll().stream().filter(r -> shift.getId().equals(r.getShiftId()))
            .sorted(Comparator.comparing(PassengerRequest::getId))
            .forEach(r -> rows.add(String.join("|", "request", r.getId(), String.valueOf(r.getStatus()),
                String.valueOf(r.getGroupId()), String.valueOf(r.getPassengerUserId()),
                String.valueOf(r.getJourneyId()), String.valueOf(r.getEvidenceImageId()), r.getEvidenceSha256(),
                String.valueOf(r.getActivatedAt()), String.valueOf(r.getReceivedAt()), r.getMessage())));
        groupsOfShift().forEach(g -> rows.add(String.join("|", "group", String.valueOf(g.getId()),
            String.valueOf(g.getStatus()), String.valueOf(g.getOpenMarker()), String.valueOf(g.getDecidedAt()),
            String.valueOf(g.getDecidedByUserId()), String.valueOf(g.getDecisionReason()),
            String.valueOf(g.getUserResponse()), String.valueOf(g.getWindowExpiresAt()),
            String.valueOf(g.getVersion()))));
        emergencyRepository.findAll().stream().filter(e -> shift.getId().equals(e.getShiftId()))
            .sorted(Comparator.comparing(Emergency::getId))
            .forEach(e -> rows.add(String.join("|", "emergency", e.getId(), String.valueOf(e.getSource()),
                String.valueOf(e.getStatus()), String.valueOf(e.getGroupId()), String.valueOf(e.getOutcome()),
                String.valueOf(e.getUserResponse()), String.valueOf(e.getClosedAt()))));
        return rows;
    }

    private record Seed(List<PassengerJourney> journeys) {}

    // An approved group with its emergency, a driver panic and a request attached to the approved group.
    private Seed seed() throws Exception {
        List<PassengerJourney> journeys = new ArrayList<>();
        for (long user : new long[] {U1, U2, U3}) {
            PassengerJourney journey = activeJourney(user);
            journeys.add(journey);
            submit(user, journey, UUID.randomUUID().toString(), clock.instant().minusSeconds(5))
                .andExpect(status().isCreated());
        }
        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(GroupStatus.AWAITING_APPROVAL, group.getStatus());
        mockMvc.perform(post("/api/v1/request-groups/" + group.getId() + "/approve").with(supervisor()))
            .andExpect(status().isCreated());
        panic();
        PassengerJourney fourth = activeJourney(U4);
        journeys.add(fourth);
        submit(U4, fourth, UUID.randomUUID().toString(), clock.instant().minusSeconds(5))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("ATTACHED"));
        return new Seed(journeys);
    }

    private static final long[] SEED_USERS = {U1, U2, U3, U4};

    @ParameterizedTest
    @ValueSource(strings = {"MANUAL", "SIGN_OUT", "AUTOMATIC_SEPARATION", "SHIFT_CLOSED"})
    void endingTheJourneysChangesNoRequestGroupOrEmergency(String reason) throws Exception {
        Seed seed = seed();
        List<String> before = snapshot();
        clock.advance(Duration.ofMinutes(2));

        if (reason.equals("SHIFT_CLOSED")) {
            closeShift();
        } else {
            for (int i = 0; i < seed.journeys().size(); i++) {
                String body = reason.equals("AUTOMATIC_SEPARATION")
                    ? "{\"reason\":\"AUTOMATIC_SEPARATION\",\"endedAt\":\"" + clock.instant().minusSeconds(30)
                        + "\",\"summary\":{\"separationDistanceMeters\":130,\"separationDurationSeconds\":65}}"
                    : "{\"reason\":\"" + reason + "\"}";
                end(SEED_USERS[i], seed.journeys().get(i), body).andExpect(status().isOk());
            }
        }

        for (PassengerJourney journey : seed.journeys()) {
            PassengerJourney stored = reload(journey);
            assertEquals(JourneyStatus.ENDED, stored.getStatus());
            assertEquals(JourneyEndReason.valueOf(reason), stored.getEndReason());
        }
        assertEquals(before, snapshot());
    }

    @Test
    void aRequestActivatedBeforeTheReportedEndArrivesAfterItAsLateAndDoesNotCountTowardTheThreshold()
            throws Exception {
        for (long user : new long[] {U1, U2}) {
            submit(user, activeJourney(user), UUID.randomUUID().toString(), clock.instant().minusSeconds(5))
                .andExpect(status().isCreated());
        }
        PassengerJourney third = activeJourney(U3);
        clock.advance(Duration.ofMinutes(3));
        Instant reportedEnd = clock.instant().minusSeconds(60);
        end(U3, third, "{\"reason\":\"AUTOMATIC_SEPARATION\",\"endedAt\":\"" + reportedEnd + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.endedAt").value(reportedEnd.toString()));
        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(GroupStatus.COLLECTING, group.getStatus());

        String id = UUID.randomUUID().toString();
        submit(U3, third, id, reportedEnd.minusSeconds(30))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("LATE"))
            .andExpect(jsonPath("$.groupStatus").doesNotExist());

        PassengerRequest stored = requestRepository.findById(id).orElseThrow();
        assertEquals(RequestStatus.LATE, stored.getStatus());
        assertNull(stored.getGroupId());
        PassengerRequestGroup after = groupRepository.findById(group.getId()).orElseThrow();
        assertEquals(GroupStatus.COLLECTING, after.getStatus());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));
        assertEquals(0, emergencyRepository.findAll().stream()
            .filter(e -> shift.getId().equals(e.getShiftId())).count());
    }

    @Test
    void aRequestActivatedAfterTheReportedEndIsRejectedAndStoresNothing() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        clock.advance(Duration.ofMinutes(3));
        Instant reportedEnd = clock.instant().minusSeconds(60);
        end(U1, journey, "{\"reason\":\"AUTOMATIC_SEPARATION\",\"endedAt\":\"" + reportedEnd + "\"}")
            .andExpect(status().isOk());
        long images = storedImagesAdded();

        submit(U1, journey, UUID.randomUUID().toString(), reportedEnd.plusSeconds(1))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ACTIVATION_AFTER_JOURNEY_END"));

        assertEquals(0, requestRepository.count());
        assertEquals(0, groupsOfShift().size());
        assertEquals(images, storedImagesAdded());
    }

    @Test
    void anEndedAtEarlierThanAnAlreadyAcceptedRequestLeavesThatRequestAndItsGroupUntouched() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        clock.advance(Duration.ofMinutes(5));
        String id = UUID.randomUUID().toString();
        Instant activatedAt = clock.instant().minusSeconds(10);
        submit(U1, journey, id, activatedAt).andExpect(status().isCreated());
        List<String> before = snapshot();

        Instant reportedEnd = clock.instant().minusSeconds(60);
        end(U1, journey, "{\"reason\":\"AUTOMATIC_SEPARATION\",\"endedAt\":\"" + reportedEnd + "\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.endedAt").value(reportedEnd.toString()));

        assertEquals(reportedEnd, reload(journey).getEndedAt());
        assertEquals(before, snapshot());
        PassengerRequest stored = requestRepository.findById(id).orElseThrow();
        assertEquals(RequestStatus.COUNTED, stored.getStatus());
        assertEquals(activatedAt, stored.getActivatedAt());
    }

    @Test
    void aRetryOfAnAlreadyAcceptedRequestAfterAnEarlierReportedEndReturnsTheOriginal() throws Exception {
        PassengerJourney journey = activeJourney(U1);
        clock.advance(Duration.ofMinutes(5));
        String id = UUID.randomUUID().toString();
        Instant activatedAt = clock.instant().minusSeconds(10);
        submit(U1, journey, id, activatedAt).andExpect(status().isCreated());
        end(U1, journey, "{\"reason\":\"AUTOMATIC_SEPARATION\",\"endedAt\":\""
            + clock.instant().minusSeconds(60) + "\"}").andExpect(status().isOk());
        List<String> before = snapshot();

        submit(U1, journey, id, activatedAt)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value(id))
            .andExpect(jsonPath("$.status").value("COUNTED"));

        assertEquals(before, snapshot());
        assertEquals(1, requestRepository.count());
    }
}
