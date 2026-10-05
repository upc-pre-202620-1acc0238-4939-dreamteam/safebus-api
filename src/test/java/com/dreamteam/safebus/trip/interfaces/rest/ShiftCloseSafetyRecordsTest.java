package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.shared.domain.model.GeoPoint;
import com.dreamteam.safebus.trip.domain.model.ShiftStatus;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US05 S2: emergencies of a shift survive its closure untouched, and reporting on a closed shift keeps working. */
class ShiftCloseSafetyRecordsTest extends AbstractShiftCloseRestTest {

    private static final Instant ACTIVATED_AT = Instant.parse("2030-07-01T09:15:30.250Z");

    private Emergency activeEmergency() {
        String id = UUID.randomUUID().toString();
        return emergencyRepository.saveAndFlush(Emergency.activateByDriver(id, company.getId(), driver.getId(),
            bus.getId(), shift.getId(), route.getId(), new GeoPoint(-12.046, -77.042), ACTIVATED_AT, clock));
    }

    private Emergency inProgressEmergency() {
        Emergency e = emergencyRepository.findById(activeEmergency().getId()).orElseThrow();
        e.startAttention(9601L, Instant.parse("2030-07-01T09:20:00.500Z"));
        return emergencyRepository.saveAndFlush(e);
    }

    private List<Object> snapshot(String id) {
        Emergency e = emergencyRepository.findById(id).orElseThrow();
        return List.of(e.getCompanyId(), e.getSource(), e.getPriority(), e.getStatus(),
            e.getDriverId(), e.getBusId(), e.getShiftId(), e.getRouteId(),
            e.getPoint().getLatitude(), e.getPoint().getLongitude(),
            java.util.Optional.ofNullable(e.getOccupancyCount()),
            e.getActivatedAt(), e.getReceivedAt(),
            java.util.Optional.ofNullable(e.getResponsibleSupervisorUserId()),
            java.util.Optional.ofNullable(e.getAttentionStartedAt()),
            java.util.Optional.ofNullable(e.getClosedAt()),
            java.util.Optional.ofNullable(e.getOutcome()),
            java.util.Optional.ofNullable(e.getUserResponse()));
    }

    private void closeShiftAsOwner() throws Exception {
        mockMvc.perform(post(closeUrl(shift.getId())).with(driverJwt(DRIVER_USER)))
            .andExpect(status().isOk());
    }

    @Test
    void activeAndInProgressEmergencies_areIdenticalAfterTheShiftCloses() throws Exception {
        String activeId = activeEmergency().getId();
        String inProgressId = inProgressEmergency().getId();
        List<Object> activeBefore = snapshot(activeId);
        List<Object> inProgressBefore = snapshot(inProgressId);

        closeShiftAsOwner();

        assertEquals(ShiftStatus.CLOSED, driverShiftRepository.findById(shift.getId()).orElseThrow().getStatus());
        assertEquals(activeBefore, snapshot(activeId));
        assertEquals(inProgressBefore, snapshot(inProgressId));
        Emergency active = emergencyRepository.findById(activeId).orElseThrow();
        assertEquals(EmergencyStatus.ACTIVE, active.getStatus());
        assertNull(active.getClosedAt());
        assertNull(active.getOutcome());
        Emergency inProgress = emergencyRepository.findById(inProgressId).orElseThrow();
        assertEquals(EmergencyStatus.IN_PROGRESS, inProgress.getStatus());
        assertEquals(9601L, inProgress.getResponsibleSupervisorUserId());
        assertNull(inProgress.getClosedAt());
    }

    @Test
    void afterTheShiftCloses_aDriverEmergencyCanStillBeCreatedForIt() throws Exception {
        closeShiftAsOwner();
        String emergencyId = UUID.randomUUID().toString();
        String body = String.format(
            "{\"id\":\"%s\",\"shiftId\":%d,\"activatedAt\":\"%s\",\"latitude\":-12.046,\"longitude\":-77.042}",
            emergencyId, shift.getId(), Instant.now(clock).minusSeconds(5));

        mockMvc.perform(post("/api/v1/driver-emergencies").with(driverJwt(DRIVER_USER))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").value(emergencyId))
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        Emergency stored = emergencyRepository.findById(emergencyId).orElseThrow();
        assertEquals(shift.getId(), stored.getShiftId());
        assertEquals(EmergencyStatus.ACTIVE, stored.getStatus());
    }

    @Test
    void afterTheShiftCloses_aLocationEventCanStillBeRecordedForIt() throws Exception {
        closeShiftAsOwner();
        String body = String.format(
            "{\"eventId\":\"%s\",\"shiftId\":%d,\"capturedAt\":\"%s\",\"accuracyMeters\":10.0,"
                + "\"latitude\":-12.046,\"longitude\":-77.042}",
            UUID.randomUUID(), shift.getId(), Instant.now(clock).minusSeconds(5));

        mockMvc.perform(post("/api/v1/location-events").with(driverJwt(DRIVER_USER))
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated());

        assertEquals(1, locationEventRepository.count());
    }
}
