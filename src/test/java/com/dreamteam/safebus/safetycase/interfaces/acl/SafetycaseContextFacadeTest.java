package com.dreamteam.safebus.safetycase.interfaces.acl;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.trip.domain.model.DriverShift;
import com.dreamteam.safebus.trip.domain.repository.DriverShiftRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class SafetycaseContextFacadeTest {

    private static final long COMPANY = 7501L;
    private static final long OTHER_COMPANY = 7502L;
    private static final Instant T0 = Instant.parse("2030-06-01T10:00:00Z");

    @Autowired SafetycaseContextFacade facade;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired DriverShiftRepository driverShiftRepository;

    private final List<String> emergencyIds = new ArrayList<>();
    private final List<DriverShift> shifts = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        emergencyRepository.deleteAllById(emergencyIds);
        driverShiftRepository.deleteAll(shifts);
    }

    private static Clock at(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }

    private Emergency activeEmergency(long companyId, long busId, long shiftId, Instant receivedAt) {
        Emergency e = emergencyRepository.save(Emergency.activateByDriver(
            UUID.randomUUID().toString(), companyId, 55L, busId, shiftId, 66L, null,
            receivedAt.minusSeconds(5), at(receivedAt)));
        emergencyIds.add(e.getId());
        return e;
    }

    // Emergency stays "new" after save, so it is reloaded before it is changed
    private Emergency inProgress(Emergency created, Instant now) {
        Emergency e = emergencyRepository.findById(created.getId()).orElseThrow();
        e.startAttention(99L, now);
        return emergencyRepository.save(e);
    }

    private Emergency closed(Emergency created, Instant now) {
        Emergency e = inProgress(created, now);
        e.close("resolved", "ok", now.plusSeconds(60));
        return emergencyRepository.save(e);
    }

    @Test
    void findOpenEmergenciesOfCompany_returnsOnlyThatCompanysEmergencies() {
        Emergency mine = activeEmergency(COMPANY, 1L, 10L, T0);
        activeEmergency(OTHER_COMPANY, 2L, 11L, T0.plusSeconds(1));

        List<SafetycaseContextFacade.OpenEmergencyView> result = facade.findOpenEmergenciesOfCompany(COMPANY);

        assertEquals(1, result.size());
        assertEquals(mine.getId(), result.get(0).emergencyId());
    }

    @Test
    void findOpenEmergenciesOfCompany_excludesClosedAndIncludesActiveAndInProgress() {
        Emergency active = activeEmergency(COMPANY, 1L, 10L, T0);
        Emergency progress = inProgress(activeEmergency(COMPANY, 2L, 11L, T0.plusSeconds(10)), T0.plusSeconds(20));
        closed(activeEmergency(COMPANY, 3L, 12L, T0.plusSeconds(30)), T0.plusSeconds(40));

        List<String> ids = facade.findOpenEmergenciesOfCompany(COMPANY).stream()
            .map(SafetycaseContextFacade.OpenEmergencyView::emergencyId).toList();

        assertEquals(2, ids.size());
        assertTrue(ids.contains(active.getId()));
        assertTrue(ids.contains(progress.getId()));
    }

    @Test
    void findOpenEmergenciesOfCompany_returnsNewestReceivedFirst() {
        Emergency oldest = activeEmergency(COMPANY, 1L, 10L, T0);
        Emergency newest = activeEmergency(COMPANY, 2L, 11L, T0.plusSeconds(120));
        Emergency middle = activeEmergency(COMPANY, 3L, 12L, T0.plusSeconds(60));

        List<String> ids = facade.findOpenEmergenciesOfCompany(COMPANY).stream()
            .map(SafetycaseContextFacade.OpenEmergencyView::emergencyId).toList();

        assertEquals(List.of(newest.getId(), middle.getId(), oldest.getId()), ids);
    }

    @Test
    void findOpenEmergenciesOfCompany_emergencyOfAClosedShiftIsStillOpen() {
        DriverShift shift = driverShiftRepository.save(DriverShift.start(7601L, 55L, 1L, 66L, at(T0)));
        shifts.add(shift);
        Emergency e = activeEmergency(COMPANY, 1L, shift.getId(), T0.plusSeconds(10));
        shift.close(T0.plusSeconds(100), null, null);
        driverShiftRepository.save(shift);

        List<SafetycaseContextFacade.OpenEmergencyView> result = facade.findOpenEmergenciesOfCompany(COMPANY);

        assertEquals(1, result.size());
        assertEquals(e.getId(), result.get(0).emergencyId());
        assertEquals(shift.getId(), result.get(0).shiftId());
    }

    @Test
    void findOpenEmergenciesOfCompany_mapsTheViewFields() {
        Emergency e = activeEmergency(COMPANY, 4L, 14L, T0);
        Emergency progress = inProgress(activeEmergency(COMPANY, 5L, 15L, T0.plusSeconds(10)), T0.plusSeconds(20));

        List<SafetycaseContextFacade.OpenEmergencyView> result = facade.findOpenEmergenciesOfCompany(COMPANY);

        SafetycaseContextFacade.OpenEmergencyView inProgressView = result.get(0);
        assertEquals(new SafetycaseContextFacade.OpenEmergencyView(progress.getId(), 5L, 15L, "DRIVER",
            "CRITICAL", "IN_PROGRESS", T0.plusSeconds(5), T0.plusSeconds(10), T0.plusSeconds(20)), inProgressView);
        SafetycaseContextFacade.OpenEmergencyView activeView = result.get(1);
        assertEquals(new SafetycaseContextFacade.OpenEmergencyView(e.getId(), 4L, 14L, "DRIVER",
            "CRITICAL", "ACTIVE", T0.minusSeconds(5), T0, null), activeView);
        assertNull(activeView.attentionStartedAt());
    }

    @Test
    void findOpenEmergenciesOfCompany_companyWithoutEmergenciesReturnsEmptyList() {
        assertTrue(facade.findOpenEmergenciesOfCompany(COMPANY).isEmpty());
    }

    @Test
    void openEmergencyView_exposesNoDriverSupervisorOutcomeCoordinatesOrRoute() {
        List<String> components = Arrays.stream(SafetycaseContextFacade.OpenEmergencyView.class.getRecordComponents())
            .map(RecordComponent::getName).toList();

        assertEquals(List.of("emergencyId", "busId", "shiftId", "source", "priority", "status",
            "activatedAt", "receivedAt", "attentionStartedAt"), components);
    }
}
