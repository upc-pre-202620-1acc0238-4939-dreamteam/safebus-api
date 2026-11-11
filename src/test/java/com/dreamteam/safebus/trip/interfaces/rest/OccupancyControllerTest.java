package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.fleet.domain.model.Company;
import com.dreamteam.safebus.passenger.interfaces.rest.JourneyResource;
import com.dreamteam.safebus.trip.support.AbstractOccupancyTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OccupancyControllerTest extends AbstractOccupancyTest {

    private RequestPostProcessor role(String role, Long companyId) {
        return jwt().jwt(b -> b.subject("42").claim("role", role).claim("companyId", companyId))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private ResultActions read(Long busId) throws Exception {
        return mockMvc.perform(get("/api/v1/buses/" + busId + "/occupancy").with(role("SUPERVISOR", company.getId())));
    }

    private void initialize() throws Exception {
        String body = "{\"initializationId\":\"" + UUID.randomUUID() + "\",\"busId\":" + bus.getId()
            + ",\"shiftId\":" + shift.getId() + ",\"confirmedCount\":0,\"confirmedAt\":\"" + clock.instant() + "\"}";
        mockMvc.perform(post("/api/v1/occupancy-initializations").header(KEY_HEADER, rawKey)
            .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is2xxSuccessful());
    }

    private ResultActions event(long sequence, String type) throws Exception {
        String body = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"busId\":" + bus.getId()
            + ",\"shiftId\":" + shift.getId() + ",\"sequence\":" + sequence + ",\"type\":\"" + type
            + "\",\"capturedAt\":\"" + clock.instant() + "\"}";
        return mockMvc.perform(post("/api/v1/occupancy-events").header(KEY_HEADER, rawKey)
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void heartbeat() throws Exception {
        mockMvc.perform(post("/api/v1/occupancy-heartbeats").header(KEY_HEADER, rawKey)
            .contentType(MediaType.APPLICATION_JSON).content("{\"busId\":" + bus.getId() + "}"))
            .andExpect(status().isOk());
    }

    @Test
    void aFreshInitializedCountIsCurrentWithCapacityAndTimes() throws Exception {
        bus.recordCapacity(40, "REF", 1L, clock);
        busRepository.save(bus);
        initialize();
        event(1, "ENTRY").andExpect(status().isCreated());
        event(2, "ENTRY").andExpect(status().isCreated());
        heartbeat();

        read(bus.getId())
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.busId").value(bus.getId()))
            .andExpect(jsonPath("$.status").value("CURRENT"))
            .andExpect(jsonPath("$.reason").doesNotExist())
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.capacity").value(40))
            .andExpect(jsonPath("$.load").value("WITHIN_CAPACITY"))
            .andExpect(jsonPath("$.lastCountAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.lastHeartbeatAt").value(clock.instant().toString()))
            .andExpect(jsonPath("$.generatedAt").value(clock.instant().toString()));
    }

    @Test
    void aCountAboveCapacityIsStillReportedAsOverCapacity() throws Exception {
        bus.recordCapacity(1, "REF", 1L, clock);
        busRepository.save(bus);
        initialize();
        event(1, "ENTRY").andExpect(status().isCreated());
        event(2, "ENTRY").andExpect(status().isCreated());
        heartbeat();

        read(bus.getId())
            .andExpect(jsonPath("$.status").value("CURRENT"))
            .andExpect(jsonPath("$.count").value(2))
            .andExpect(jsonPath("$.load").value("OVER_CAPACITY"));
    }

    @Test
    void aBusWithoutCapacityReportsCapacityUnknown() throws Exception {
        initialize();
        heartbeat();

        read(bus.getId())
            .andExpect(jsonPath("$.count").value(0))
            .andExpect(jsonPath("$.capacity").doesNotExist())
            .andExpect(jsonPath("$.load").value("CAPACITY_UNKNOWN"));
    }

    @Test
    void theCountIsStaleOnlyWhenTheHeartbeatIsOlderThanTwoMinutes() throws Exception {
        initialize();
        heartbeat();

        clock.advance(Duration.ofMinutes(2));
        read(bus.getId()).andExpect(jsonPath("$.status").value("CURRENT")).andExpect(jsonPath("$.count").value(0));

        clock.advance(Duration.ofSeconds(1));
        read(bus.getId()).andExpect(jsonPath("$.status").value("STALE")).andExpect(jsonPath("$.count").value(0));

        heartbeat();
        read(bus.getId()).andExpect(jsonPath("$.status").value("CURRENT"));
    }

    @Test
    void aCountThatNeverSentAHeartbeatIsStale() throws Exception {
        initialize();

        read(bus.getId())
            .andExpect(jsonPath("$.status").value("STALE"))
            .andExpect(jsonPath("$.lastHeartbeatAt").doesNotExist());
    }

    @Test
    void anUninitializedBusIsUnavailableWithoutACount() throws Exception {
        read(bus.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("NOT_INITIALIZED"))
            .andExpect(jsonPath("$.count").doesNotExist())
            .andExpect(jsonPath("$.load").doesNotExist());
    }

    @Test
    void aBusWithoutAnOccupancyRowIsUnavailable() throws Exception {
        Bus other = anotherBusOfTheCompany();

        read(other.getId())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("NOT_INITIALIZED"));
    }

    @Test
    void aSequenceGapMakesTheCountUnavailableUntilTheReplayCompletes() throws Exception {
        initialize();
        heartbeat();
        event(1, "ENTRY").andExpect(status().isCreated());
        event(3, "ENTRY").andExpect(status().isConflict());

        read(bus.getId())
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("SEQUENCE_GAP"))
            .andExpect(jsonPath("$.count").doesNotExist());

        event(2, "ENTRY").andExpect(status().isCreated());
        read(bus.getId()).andExpect(jsonPath("$.status").value("UNAVAILABLE"));

        event(3, "ENTRY").andExpect(status().isCreated());
        read(bus.getId())
            .andExpect(jsonPath("$.status").value("CURRENT"))
            .andExpect(jsonPath("$.count").value(3));
    }

    @Test
    void anInconsistentCountIsUnavailableAndNothingElseIsExposed() throws Exception {
        initialize();
        heartbeat();
        event(1, "EXIT").andExpect(status().isUnprocessableEntity());

        read(bus.getId())
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("INCONSISTENT_COUNT"))
            .andExpect(jsonPath("$.count").doesNotExist())
            .andExpect(jsonPath("$.load").doesNotExist());
    }

    @Test
    void aCountOfAClosedShiftIsUnavailable() throws Exception {
        initialize();
        heartbeat();
        closeShift(shift);

        read(bus.getId())
            .andExpect(jsonPath("$.status").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.reason").value("NOT_INITIALIZED"))
            .andExpect(jsonPath("$.count").doesNotExist());
    }

    @Test
    void aPassengerGets403() throws Exception {
        mockMvc.perform(get("/api/v1/buses/" + bus.getId() + "/occupancy")
                .with(role("PASSENGER", company.getId())))
            .andExpect(status().isForbidden());
    }

    @Test
    void aDriverGets403() throws Exception {
        mockMvc.perform(get("/api/v1/buses/" + bus.getId() + "/occupancy")
                .with(role("DRIVER", company.getId())))
            .andExpect(status().isForbidden());
    }

    @Test
    void noTokenGets401() throws Exception {
        mockMvc.perform(get("/api/v1/buses/" + bus.getId() + "/occupancy"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void aForeignBusAndAMissingBusGetTheSameDenial() throws Exception {
        Company other = companyRepository.save(Company.create("OC Foreign " + UUID.randomUUID()));
        try {
            MvcResult foreign = mockMvc.perform(get("/api/v1/buses/" + bus.getId() + "/occupancy")
                    .with(role("SUPERVISOR", other.getId())))
                .andExpect(status().isForbidden()).andReturn();
            MvcResult missing = mockMvc.perform(get("/api/v1/buses/999999999/occupancy")
                    .with(role("SUPERVISOR", company.getId())))
                .andExpect(status().isForbidden()).andReturn();

            String a = foreign.getResponse().getContentAsString();
            String b = missing.getResponse().getContentAsString();
            assertEquals(com.jayway.jsonpath.JsonPath.<String>read(a, "$.code"),
                com.jayway.jsonpath.JsonPath.<String>read(b, "$.code"));
            assertEquals("BUS_ACCESS_DENIED", com.jayway.jsonpath.JsonPath.<String>read(a, "$.code"));
            assertEquals(com.jayway.jsonpath.JsonPath.<String>read(a, "$.detail"),
                com.jayway.jsonpath.JsonPath.<String>read(b, "$.detail"));
        } finally {
            companyRepository.delete(other);
        }
    }

    @Test
    void theDeviceKeyCannotReadOccupancy() throws Exception {
        mockMvc.perform(get("/api/v1/buses/" + bus.getId() + "/occupancy").header(KEY_HEADER, rawKey))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void thePassengerBusResponsesCarryNoOccupancyValues() {
        List<String> names = new ArrayList<>();
        collect(JourneyResource.class, names);
        collect(VehicleLocationResource.class, names);

        assertFalse(names.isEmpty());
        for (String name : names) {
            String lower = name.toLowerCase();
            assertFalse(lower.contains("occupancy") || lower.contains("count") || lower.contains("load")
                || lower.contains("capacity") || lower.contains("heartbeat"), name);
        }
        assertTrue(names.contains("plate"));
    }

    private void collect(Class<?> type, List<String> names) {
        for (RecordComponent c : type.getRecordComponents()) {
            names.add(c.getName());
            if (c.getType().isRecord()) {
                collect(c.getType(), names);
            }
        }
    }
}
