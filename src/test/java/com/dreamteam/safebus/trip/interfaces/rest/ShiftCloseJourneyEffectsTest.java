package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.passenger.domain.model.JourneyEndReason;
import com.dreamteam.safebus.passenger.domain.model.JourneyStatus;
import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US05 S1: what closing a shift does to journeys, location access and the driver's assignment. */
class ShiftCloseJourneyEffectsTest extends AbstractShiftCloseRestTest {

    private void postLocation() throws Exception {
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,"
                + "\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(5));
        mockMvc.perform(post("/api/v1/location-events").with(driverJwt(DRIVER_USER))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());
    }

    private long startJourney(long passengerUserId) throws Exception {
        String response = mockMvc.perform(post("/api/v1/journeys").with(passengerJwt(passengerUserId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"" + BUS_QR + "\"}"))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.journeyId")).longValue();
    }

    private void closeShiftAsOwner() throws Exception {
        mockMvc.perform(post(closeUrl(shift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk());
    }

    @Test
    void closingTheShift_endsTheActiveJourneysOfThatShiftWithShiftClosed() throws Exception {
        postLocation();
        long journeyId = startJourney(PASSENGER_USER);
        PassengerJourney onOtherShift = journeyRepository.saveAndFlush(
            PassengerJourney.start(OTHER_PASSENGER_USER, otherBus.getId(), otherShift.getId(), clock));

        closeShiftAsOwner();

        PassengerJourney ended = journeyRepository.findById(journeyId).orElseThrow();
        assertEquals(JourneyStatus.ENDED, ended.getStatus());
        assertEquals(JourneyEndReason.SHIFT_CLOSED, ended.getEndReason());
        assertEquals(driverShiftRepository.findById(shift.getId()).orElseThrow().getClosedAt(), ended.getEndedAt());
        assertNull(ended.getActiveMarker());
        PassengerJourney untouched = journeyRepository.findById(onOtherShift.getId()).orElseThrow();
        assertEquals(JourneyStatus.ACTIVE, untouched.getStatus());
        assertNull(untouched.getEndReason());
    }

    @Test
    void afterClosing_thePassengerNoLongerSeesTheBusLocation() throws Exception {
        postLocation();
        startJourney(PASSENGER_USER);
        String locationUrl = "/api/v1/vehicles/" + bus.getId() + "/location";
        mockMvc.perform(get(locationUrl).with(passengerJwt(PASSENGER_USER)))
            .andExpect(status().isOk());

        closeShiftAsOwner();

        mockMvc.perform(get(locationUrl).with(passengerJwt(PASSENGER_USER)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("BUS_ACCESS_DENIED"));
    }

    @Test
    void afterClosing_aNewJourneyOnThatBusIsRejectedAsNotInService() throws Exception {
        closeShiftAsOwner();

        mockMvc.perform(post("/api/v1/journeys").with(passengerJwt(OTHER_PASSENGER_USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"busQrCode\":\"" + BUS_QR + "\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("BUS_NOT_IN_SERVICE"));
    }

    @Test
    void afterClosing_theDriverHasNoCurrentAssignment() throws Exception {
        mockMvc.perform(get("/api/v1/shifts/me/assignment").with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk());

        closeShiftAsOwner();

        mockMvc.perform(get("/api/v1/shifts/me/assignment").with(driverJwt(DRIVER_USER)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void afterClosing_thePassengerEndingTheJourneyGets200UnchangedWithShiftClosed() throws Exception {
        long journeyId = startJourney(PASSENGER_USER);
        closeShiftAsOwner();
        PassengerJourney ended = journeyRepository.findById(journeyId).orElseThrow();

        mockMvc.perform(post("/api/v1/journeys/" + journeyId + "/end").with(passengerJwt(PASSENGER_USER)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.journeyId").value(journeyId))
            .andExpect(jsonPath("$.status").value("ENDED"))
            .andExpect(jsonPath("$.endReason").value("SHIFT_CLOSED"));

        PassengerJourney after = journeyRepository.findById(journeyId).orElseThrow();
        assertEquals(JourneyEndReason.SHIFT_CLOSED, after.getEndReason());
        assertEquals(ended.getEndedAt(), after.getEndedAt());
    }
}
