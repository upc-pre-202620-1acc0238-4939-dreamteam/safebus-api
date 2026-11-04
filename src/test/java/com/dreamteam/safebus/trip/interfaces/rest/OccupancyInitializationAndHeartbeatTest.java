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

class OccupancyInitializationAndHeartbeatTest extends AbstractOccupancyTest {

    private static final String INIT = "/api/v1/occupancy-initializations";
    private static final String HEARTBEAT = "/api/v1/occupancy-heartbeats";

    private String initBody(String initializationId, Long busId, Long shiftId, Object count, String confirmedAt) {
        return "{\"initializationId\":\"" + initializationId + "\",\"busId\":" + busId
            + ",\"shiftId\":" + shiftId + ",\"confirmedCount\":" + count
            + ",\"confirmedAt\":\"" + confirmedAt + "\"}";
    }

    private String validInit(String initializationId) {
        return initBody(initializationId, bus.getId(), shift.getId(), 0, clock.instant().toString());
    }

    private ResultActions initialize(String key, String body) throws Exception {
        var request = post(INIT).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header(KEY_HEADER, key);
        }
        return mockMvc.perform(request);
    }

    private ResultActions heartbeat(String key, String body) throws Exception {
        var request = post(HEARTBEAT).contentType(MediaType.APPLICATION_JSON).content(body);
        if (key != null) {
            request.header(KEY_HEADER, key);
        }
        return mockMvc.perform(request);
    }

    private BusOccupancy stored() {
        return occupancyRepository.findByBusId(bus.getId()).orElseThrow();
    }

    @Test
    void initializationReturns201AndStartsTheCountAtZero() throws Exception {
        String id = UUID.randomUUID().toString();

        initialize(rawKey, validInit(id))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.initializationId").value(id))
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.shiftId").value(shift.getId()))
            .andExpect(jsonPath("$.confirmedCount").value(0));

        BusOccupancy o = stored();
        assertEquals(ConsistencyState.CONSISTENT, o.getConsistency());
        assertEquals(0, o.getCount());
        assertEquals(shift.getId(), o.getShiftId());
        assertEquals(clock.instant(), o.getLastCountAt());
    }

    @Test
    void anIdenticalRetryReturns200AndChangesNothing() throws Exception {
        String id = UUID.randomUUID().toString();
        initialize(rawKey, validInit(id)).andExpect(status().isCreated());
        int epoch = stored().getEpoch();

        initialize(rawKey, validInit(id)).andExpect(status().isOk());

        assertEquals(epoch, stored().getEpoch());
    }

    @Test
    void aSecondInitializationOfAConsistentCountConflicts() throws Exception {
        initialize(rawKey, validInit(UUID.randomUUID().toString())).andExpect(status().isCreated());

        initialize(rawKey, validInit(UUID.randomUUID().toString()))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("ALREADY_INITIALIZED"));
    }

    @Test
    void initializationRejectsANonZeroCount() throws Exception {
        initialize(rawKey, initBody(UUID.randomUUID().toString(), bus.getId(), shift.getId(), 3,
            clock.instant().toString()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_INITIAL_COUNT"));
        assertEquals(ConsistencyState.NOT_INITIALIZED, stored().getConsistency());
    }

    @Test
    void initializationRejectsAFutureConfirmationTime() throws Exception {
        initialize(rawKey, initBody(UUID.randomUUID().toString(), bus.getId(), shift.getId(), 0,
            clock.instant().plus(Duration.ofMinutes(6)).toString()))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("INVALID_CAPTURE_TIME"));
    }

    @Test
    void malformedInitializationBodiesAre422() throws Exception {
        String ok = UUID.randomUUID().toString();
        initialize(rawKey, "{\"initializationId\":\"" + ok + "\"}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        initialize(rawKey, validInit("not-a-uuid"))
            .andExpect(status().isUnprocessableEntity());
        initialize(rawKey, "not json")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        initialize(rawKey, initBody(ok, bus.getId(), shift.getId(), "\"zero\"", clock.instant().toString()))
            .andExpect(status().isUnprocessableEntity());
        initialize(rawKey, initBody(ok, bus.getId(), shift.getId(), 0, "yesterday"))
            .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void noCredentialGets401() throws Exception {
        initialize(null, validInit(UUID.randomUUID().toString())).andExpect(status().isUnauthorized());
        heartbeat(null, "{\"busId\":" + bus.getId() + "}").andExpect(status().isUnauthorized());
    }

    @Test
    void wrongRevokedAndRotatedKeysAndOtherBusesAllGetTheSameDenial() throws Exception {
        Bus otherBus = anotherBusOfTheCompany();
        String body = validInit(UUID.randomUUID().toString());
        String otherBusBody = initBody(UUID.randomUUID().toString(), otherBus.getId(), shift.getId(), 0,
            clock.instant().toString());

        MvcResult wrong = initialize("sbk_wrong", body).andExpect(status().isForbidden()).andReturn();
        MvcResult otherBusResult = initialize(rawKey, otherBusBody).andExpect(status().isForbidden()).andReturn();

        source.revoke(clock);
        sourceRepository.save(source);
        MvcResult revoked = initialize(rawKey, body).andExpect(status().isForbidden()).andReturn();

        String wrongBody = wrong.getResponse().getContentAsString();
        for (MvcResult r : new MvcResult[] {otherBusResult, revoked}) {
            assertEquals(JsonPath.<String>read(wrongBody, "$.code"),
                JsonPath.<String>read(r.getResponse().getContentAsString(), "$.code"));
            assertEquals(JsonPath.<String>read(wrongBody, "$.detail"),
                JsonPath.<String>read(r.getResponse().getContentAsString(), "$.detail"));
            assertEquals(JsonPath.<String>read(wrongBody, "$.title"),
                JsonPath.<String>read(r.getResponse().getContentAsString(), "$.title"));
            assertEquals(r.getResponse().getContentType().split(";")[0],
                wrong.getResponse().getContentType().split(";")[0]);
        }
        assertEquals("OCCUPANCY_SOURCE_NOT_AUTHORIZED", JsonPath.<String>read(wrongBody, "$.code"));
        assertEquals("access denied", JsonPath.<String>read(wrongBody, "$.detail"));
    }

    @Test
    void aShiftThatIsNotTheActiveOneOfTheBusIsDenied() throws Exception {
        Bus otherBus = anotherBusOfTheCompany();
        var foreignShift = startShiftOn(otherBus);
        initialize(rawKey, initBody(UUID.randomUUID().toString(), bus.getId(), foreignShift.getId(), 0,
            clock.instant().toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"));

        closeShift(shift);
        initialize(rawKey, validInit(UUID.randomUUID().toString()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"));
        assertEquals(ConsistencyState.NOT_INITIALIZED, stored().getConsistency());
    }

    @Test
    void aNewShiftOnTheSameBusCanBeInitializedAgain() throws Exception {
        initialize(rawKey, validInit(UUID.randomUUID().toString())).andExpect(status().isCreated());
        closeShift(shift);
        var newShift = startShiftOn(bus);

        initialize(rawKey, initBody(UUID.randomUUID().toString(), bus.getId(), newShift.getId(), 0,
            clock.instant().toString()))
            .andExpect(status().isCreated());

        assertEquals(newShift.getId(), stored().getShiftId());
    }

    @Test
    void heartbeatRecordsTheServerTime() throws Exception {
        clock.advance(Duration.ofSeconds(90));

        heartbeat(rawKey, "{\"busId\":" + bus.getId() + "}")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.receivedAt").value(clock.instant().toString()));

        assertEquals(clock.instant(), stored().getLastHeartbeatAt());
    }

    @Test
    void heartbeatForAnotherBusIsDeniedAndRecordsNothing() throws Exception {
        Bus otherBus = anotherBusOfTheCompany();

        heartbeat(rawKey, "{\"busId\":" + otherBus.getId() + "}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"))
            .andExpect(jsonPath("$.detail").value("access denied"));
        assertNull(stored().getLastHeartbeatAt());
    }

    @Test
    void heartbeatWithAWrongKeyIsDenied() throws Exception {
        heartbeat("sbk_wrong", "{\"busId\":" + bus.getId() + "}")
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("OCCUPANCY_SOURCE_NOT_AUTHORIZED"));
    }

    @Test
    void heartbeatWithoutABusIsMalformed() throws Exception {
        heartbeat(rawKey, "{}")
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void onlyAHeartbeatMovesTheHeartbeatTime() throws Exception {
        initialize(rawKey, validInit(UUID.randomUUID().toString())).andExpect(status().isCreated());

        assertNull(stored().getLastHeartbeatAt());
    }
}
