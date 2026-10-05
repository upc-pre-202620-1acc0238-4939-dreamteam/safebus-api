package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.AssignmentStatus;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ShiftCloseControllerTest extends AbstractShiftCloseRestTest {

    private void assertShiftAndJourneyUntouched(DriverShift s, PassengerJourney journey) {
        DriverShift stored = driverShiftRepository.findById(s.getId()).orElseThrow();
        assertEquals(ShiftStatus.ACTIVE, stored.getStatus());
        assertNull(stored.getClosedAt());
        assertNull(stored.getLastKnownPoint());
        assertNull(stored.getLastKnownCapturedAt());
        assertEquals(AssignmentStatus.ACTIVE,
            assignmentRepository.findById(s.getAssignmentId()).orElseThrow().getStatus());
        PassengerJourney j = journeyRepository.findById(journey.getId()).orElseThrow();
        assertEquals(JourneyStatus.ACTIVE, j.getStatus());
        assertNull(j.getEndReason());
        assertNull(j.getEndedAt());
    }

    @Test
    void owner_returns200WithExactlyShiftIdStatusAndClosedAt() throws Exception {
        MvcResult result = mockMvc.perform(post(closeUrl(shift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.shiftId").value(shift.getId()))
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closedAt").isString())
            .andExpect(jsonPath("$.length()").value(3))
            .andReturn();

        DriverShift stored = driverShiftRepository.findById(shift.getId()).orElseThrow();
        assertEquals(ShiftStatus.CLOSED, stored.getStatus());
        String closedAt = JsonPath.read(result.getResponse().getContentAsString(), "$.closedAt");
        assertEquals(stored.getClosedAt(), Instant.parse(closedAt));
        assertEquals(AssignmentStatus.CLOSED,
            assignmentRepository.findById(shift.getAssignmentId()).orElseThrow().getStatus());
    }

    @Test
    void closingTwice_returnsTheSameBodyWithTheOriginalClosedAt() throws Exception {
        String first = mockMvc.perform(post(closeUrl(shift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Instant storedClosedAt = driverShiftRepository.findById(shift.getId()).orElseThrow().getClosedAt();

        String second = mockMvc.perform(post(closeUrl(shift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(first, second);
        assertEquals(storedClosedAt, driverShiftRepository.findById(shift.getId()).orElseThrow().getClosedAt());
    }

    @Test
    void anotherDriversShift_returns403ShiftNotAuthorizedAndChangesNothing() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER, otherBus.getId(), otherShift.getId(), clock));

        mockMvc.perform(post(closeUrl(otherShift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("SHIFT_NOT_AUTHORIZED"));

        assertShiftAndJourneyUntouched(otherShift, journey);
    }

    @Test
    void nonExistentShift_returns403WithSameStatusCodeAndDetailAsAnotherDriversShift() throws Exception {
        MvcResult foreign = mockMvc.perform(post(closeUrl(otherShift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isForbidden()).andReturn();
        MvcResult missing = mockMvc.perform(post(closeUrl(987654321L)).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isForbidden()).andReturn();

        String foreignBody = foreign.getResponse().getContentAsString();
        String missingBody = missing.getResponse().getContentAsString();
        assertEquals("SHIFT_NOT_AUTHORIZED", JsonPath.read(missingBody, "$.code"));
        assertEquals((String) JsonPath.read(foreignBody, "$.code"), JsonPath.read(missingBody, "$.code"));
        assertEquals((String) JsonPath.read(foreignBody, "$.detail"), JsonPath.read(missingBody, "$.detail"));
    }

    @Test
    void supervisorAndPassenger_return403AndChangeNothing() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER, bus.getId(), shift.getId(), clock));

        mockMvc.perform(post(closeUrl(shift.getId())).with(supervisorJwt()))
            .andExpect(status().isForbidden());
        mockMvc.perform(post(closeUrl(shift.getId())).with(passengerJwt(PASSENGER_USER)))
            .andExpect(status().isForbidden());

        assertShiftAndJourneyUntouched(shift, journey);
    }

    @Test
    void noToken_returns401AndChangesNothing() throws Exception {
        PassengerJourney journey = journeyRepository.saveAndFlush(
            PassengerJourney.start(PASSENGER_USER, bus.getId(), shift.getId(), clock));

        mockMvc.perform(post(closeUrl(shift.getId())))
            .andExpect(status().isUnauthorized());

        assertShiftAndJourneyUntouched(shift, journey);
    }
}
