package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.ConsistencyState;
import com.dreamteam.safebus.trip.support.AbstractOccupancyTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OccupancyEventControllerTest extends AbstractOccupancyTest {

    private static final String INIT = "/api/v1/occupancy-initializations";
    private static final String EVENTS = "/api/v1/occupancy-events";

    private void initialize() throws Exception {
        initializeShift(shift.getId());
    }

    private void initializeShift(Long shiftId) throws Exception {
        String body = "{\"initializationId\":\"" + UUID.randomUUID() + "\",\"busId\":" + bus.getId()
            + ",\"shiftId\":" + shiftId + ",\"confirmedCount\":0,\"confirmedAt\":\""
            + clock.instant() + "\"}";
        mockMvc.perform(post(INIT).header(KEY_HEADER, rawKey).contentType(MediaType.APPLICATION_JSON)
            .content(body)).andExpect(status().is2xxSuccessful());
    }

    private String eventBody(String eventId, Long busId, Long shiftId, Object sequence, String type,
                             String capturedAt) {
        return "{\"eventId\":\"" + eventId + "\",\"busId\":" + busId + ",\"shiftId\":" + shiftId
            + ",\"sequence\":" + sequence + ",\"type\":\"" + type + "\",\"capturedAt\":\"" + capturedAt + "\"}";
    }

    private String event(long sequence, String type) {
        return eventBody(UUID.randomUUID().toString(), bus.getId(), shift.getId(), sequence, type,
            clock.instant().toString());
    }

    private ResultActions send(String key, String body) throws Exception {
        var request = post(EVENTS).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header(KEY_HEADER, key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions send(String body) throws Exception {
        return send(rawKey, body);
    }

    private BusOccupancy stored() {
        return occupancyRepository.findByBusId(bus.getId()).orElseThrow();
    }

    private long storedEvents() {
        return jdbcTemplate.queryForObject("select count(*) from occupancy_event where bus_id = ?",
            Long.class, bus.getId());
    }

    @Test
    void theFirstEventReturns201WithTheNewCount() throws Exception {
        initialize();

        send(event(1, "ENTRY"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sequence").value(1))
            .andExpect(jsonPath("$.type").value("ENTRY"))
            .andExpect(jsonPath("$.count").value(1));

        assertEquals(1, stored().getCount());
        assertEquals(1, storedEvents());
    }

    @Test
    void subsequentEventsMoveTheCountUpAndDown() throws Exception {
        initialize();

        send(event(1, "ENTRY")).andExpect(status().isCreated());
        send(event(2, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(2));
        send(event(3, "EXIT")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(1));

        assertEquals(1, stored().getCount());
        assertEquals(3, stored().getLastSequence());
    }

    @Test
    void anIdenticalRetryReturns200AndStoresNothingNew() throws Exception {
        initialize();
        String body = event(1, "ENTRY");
        send(body).andExpect(status().isCreated());
        send(event(2, "ENTRY")).andExpect(status().isCreated());

        send(body)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sequence").value(1))
            .andExpect(jsonPath("$.count").value(1));

        assertEquals(2, stored().getCount());
        assertEquals(2, storedEvents());
    }

    @Test
    void reusingAnEventIdWithAnotherPayloadConflicts() throws Exception {
        initialize();
        String id = UUID.randomUUID().toString();
        String instant = clock.instant().toString();
        send(eventBody(id, bus.getId(), shift.getId(), 1, "ENTRY", instant)).andExpect(status().isCreated());

        send(eventBody(id, bus.getId(), shift.getId(), 2, "ENTRY", instant))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EVENT_ID_REUSED"));

        assertEquals(1, stored().getCount());
    }

    @Test
    void aSequenceAlreadyAppliedUnderAnotherIdConflicts() throws Exception {
        initialize();
        send(event(1, "ENTRY")).andExpect(status().isCreated());

        send(event(1, "ENTRY"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEQUENCE_ALREADY_APPLIED"));

        assertEquals(1, stored().getCount());
        assertEquals(1, storedEvents());
    }

    @Test
    void aGapReturns409WithTheExpectedSequenceAndStoresTheMark() throws Exception {
        initialize();
        send(event(1, "ENTRY")).andExpect(status().isCreated());

        send(event(3, "ENTRY"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("SEQUENCE_GAP"))
            .andExpect(jsonPath("$.expectedSequence").value(2));

        BusOccupancy o = stored();
        assertEquals(ConsistencyState.GAP, o.getConsistency());
        assertEquals(3L, o.getRecoverAtSequence());
        assertEquals(1, o.getCount());
        assertEquals(1, storedEvents());
    }

    @Test
    void theGapIsRecoveredWhenTheReplayReachesTheHighestSequenceSeen() throws Exception {
        initialize();
        send(event(1, "ENTRY")).andExpect(status().isCreated());
        send(event(3, "ENTRY")).andExpect(status().isConflict());

        send(event(2, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(2));
        assertEquals(ConsistencyState.GAP, stored().getConsistency());

        send(event(3, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(3));
        BusOccupancy o = stored();
        assertEquals(ConsistencyState.CONSISTENT, o.getConsistency());
        assertNull(o.getRecoverAtSequence());
    }

    @Test
    void aFurtherAheadSequenceRaisesTheRecoveryPoint() throws Exception {
        initialize();
        send(event(1, "ENTRY")).andExpect(status().isCreated());
        send(event(3, "ENTRY")).andExpect(status().isConflict());
        send(event(5, "ENTRY"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.expectedSequence").value(2));
        assertEquals(5L, stored().getRecoverAtSequence());

        send(event(2, "ENTRY")).andExpect(status().isCreated());
        send(event(3, "ENTRY")).andExpect(status().isCreated());
        send(event(4, "ENTRY")).andExpect(status().isCreated());
        assertEquals(ConsistencyState.GAP, stored().getConsistency());

        send(event(5, "ENTRY")).andExpect(status().isCreated());
        assertEquals(ConsistencyState.CONSISTENT, stored().getConsistency());
        assertEquals(5, stored().getCount());
    }

    @Test
    void anExitBelowZeroReturns422AndMarksTheCountInconsistent() throws Exception {
        initialize();

        send(event(1, "EXIT"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("NEGATIVE_COUNT"));

        BusOccupancy o = stored();
        assertEquals(ConsistencyState.INCONSISTENT, o.getConsistency());
        assertEquals(0, o.getCount());
        assertEquals(0, storedEvents());
    }

    @Test
    void anInconsistentCountRejectsEventsUntilItIsReinitialized() throws Exception {
        initialize();
        send(event(1, "EXIT")).andExpect(status().isUnprocessableEntity());

        send(event(1, "ENTRY"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_INCONSISTENT"));

        initialize();
        send(event(1, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(1));
        assertEquals(ConsistencyState.CONSISTENT, stored().getConsistency());
    }

    @Test
    void eventsBeforeInitializationAreRejected() throws Exception {
        send(event(1, "ENTRY"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_NOT_INITIALIZED"));
    }

    @Test
    void eventsOfAnotherShiftAreRejected() throws Exception {
        initialize();
        closeShift(shift);
        var newShift = startShiftOn(bus);

        send(eventBody(UUID.randomUUID().toString(), bus.getId(), newShift.getId(), 1, "ENTRY",
            clock.instant().toString()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_NOT_INITIALIZED"));
    }

    @Test
    void aCaptureTimeUpToFiveMinutesAheadIsAcceptedAndBeyondIsRejected() throws Exception {
        initialize();

        send(eventBody(UUID.randomUUID().toString(), bus.getId(), shift.getId(), 1, "ENTRY",
            clock.instant().plus(Duration.ofMinutes(5)).toString()))
            .andExpect(status().isCreated());
        send(eventBody(UUID.randomUUID().toString(), bus.getId(), shift.getId(), 2, "ENTRY",
            clock.instant().plus(Duration.ofMinutes(5)).plusSeconds(1).toString()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CAPTURE_TIME"));

        assertEquals(1, stored().getCount());
    }

    @Test
    void malformedBodiesAre422() throws Exception {
        initialize();
        String id = UUID.randomUUID().toString();
        String now = clock.instant().toString();

        send("{\"eventId\":\"" + id + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        send(eventBody("not-a-uuid", bus.getId(), shift.getId(), 1, "ENTRY", now))
            .andExpect(status().isUnprocessableEntity());
        send(eventBody(id, bus.getId(), shift.getId(), 0, "ENTRY", now))
            .andExpect(status().isUnprocessableEntity());
        send(eventBody(id, bus.getId(), shift.getId(), -1, "ENTRY", now))
            .andExpect(status().isUnprocessableEntity());
        send(eventBody(id, bus.getId(), shift.getId(), "\"one\"", "ENTRY", now))
            .andExpect(status().isUnprocessableEntity());
        send(eventBody(id, bus.getId(), shift.getId(), 1, "SIDEWAYS", now))
            .andExpect(status().isUnprocessableEntity());
        send(eventBody(id, bus.getId(), shift.getId(), 1, "ENTRY", "yesterday"))
            .andExpect(status().isUnprocessableEntity());
        send("not json")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        assertEquals(0, stored().getCount());
        assertEquals(0, storedEvents());
    }

    @Test
    void noCredentialGets401() throws Exception {
        initialize();

        send(null, event(1, "ENTRY")).andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRevokedAndOtherBusRequestsGetTheSameDenialAndChangeNothing() throws Exception {
        initialize();
        Bus otherBus = anotherBusOfTheCompany();
        String otherBusBody = eventBody(UUID.randomUUID().toString(), otherBus.getId(), shift.getId(), 1,
            "ENTRY", clock.instant().toString());

        MvcResult wrong = send("sbk_wrong", event(1, "ENTRY")).andExpect(status().isForbidden()).andReturn();
        MvcResult otherBusResult = send(otherBusBody).andExpect(status().isForbidden()).andReturn();
        source.revoke(clock);
        sourceRepository.save(source);
        MvcResult revoked = send(event(1, "ENTRY")).andExpect(status().isForbidden()).andReturn();

        String reference = wrong.getResponse().getContentAsString();
        for (MvcResult r : new MvcResult[] {otherBusResult, revoked}) {
            String content = r.getResponse().getContentAsString();
            assertEquals(JsonPath.<String>read(reference, "$.code"), JsonPath.<String>read(content, "$.code"));
            assertEquals(JsonPath.<String>read(reference, "$.detail"), JsonPath.<String>read(content, "$.detail"));
            assertEquals(JsonPath.<String>read(reference, "$.title"), JsonPath.<String>read(content, "$.title"));
        }
        assertEquals("OCCUPANCY_SOURCE_NOT_AUTHORIZED", JsonPath.<String>read(reference, "$.code"));
        assertEquals(0, stored().getCount());
        assertEquals(0, storedEvents());
    }

    @Test
    void theLoadFollowsTheCapacityOfTheBus() throws Exception {
        initialize();

        send(event(1, "ENTRY")).andExpect(jsonPath("$.load").value("CAPACITY_UNKNOWN"));

        bus.recordCapacity(2, "REF", 1L, clock);
        busRepository.save(bus);

        send(event(2, "ENTRY")).andExpect(jsonPath("$.load").value("WITHIN_CAPACITY"));
        send(event(3, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.load").value("OVER_CAPACITY"));
        assertEquals(3, stored().getCount());
    }

    @Test
    void theCountMayExceedTheCapacity() throws Exception {
        initialize();
        bus.recordCapacity(1, "REF", 1L, clock);
        busRepository.save(bus);

        send(event(1, "ENTRY")).andExpect(jsonPath("$.load").value("WITHIN_CAPACITY"));
        send(event(2, "ENTRY")).andExpect(status().isCreated()).andExpect(jsonPath("$.count").value(2));
    }
}
