package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

class ClosePassengerEmergencyTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 73001L;
    private static final long PASSENGER = 73101L;

    @Autowired ApproveGroupCommandService approveService;
    @Autowired StartAttentionCommandService attentionService;
    @Autowired CloseEmergencyCommandService closeService;
    @Autowired RegisterPassengerRequest registerRequest;
    @MockitoBean CurrentUserProvider currentUser;

    @BeforeEach
    void actAsSupervisor() {
        asSupervisor(company.getId());
    }

    private void asSupervisor(Long companyId) {
        when(currentUser.current()).thenReturn(new AuthenticatedUser(SUPERVISOR, "SUPERVISOR", companyId));
    }

    private String approvedEmergencyId(PassengerRequestGroup group) {
        return approveService.approve(new ApproveGroupCommand(group.getId())).emergencyId();
    }

    private PassengerRequestGroup reload(PassengerRequestGroup group) {
        return groupRepository.findById(group.getId()).orElseThrow();
    }

    private CloseEmergencyCommand close(String emergencyId) {
        return new CloseEmergencyCommand(emergencyId, "Resolved", "Thanks");
    }

    @Test
    void closingTheEmergencyClosesTheApprovedGroupAndFreesTheShift() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);
        attentionService.startAttention(emergencyId);

        CloseEmergencyResult result = closeService.close(close(emergencyId));

        assertEquals("CLOSED", result.status());
        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.CLOSED, stored.getStatus());
        assertNull(stored.getOpenMarker());
        assertEquals(EmergencyStatus.CLOSED, emergencyRepository.findById(emergencyId).orElseThrow().getStatus());
    }

    @Test
    void anEligibleRequestAfterTheClosureStartsANewGroup() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);
        attentionService.startAttention(emergencyId);
        closeService.close(close(emergencyId));
        when(currentUser.current()).thenReturn(new AuthenticatedUser(PASSENGER, "PASSENGER", null));

        RegisterPassengerRequestResult result = registerRequest.register(new RegisterPassengerRequestCommand(
            UUID.randomUUID().toString(), activeJourney(PASSENGER).getId(), "Danger on board",
            clock.instant().minusSeconds(5), TestImages.jpeg(9)));

        assertEquals("COUNTED", result.status());
        assertEquals("COLLECTING", result.groupStatus());
        assertEquals(2, groupsOfShift().size());
        assertEquals(GroupStatus.CLOSED, reload(group).getStatus());
        assertEquals(GroupStatus.COLLECTING, groupsOfShift().get(1).getStatus());
        assertEquals(1, emergencyRepository.findAll().stream()
            .filter(e -> group.getId().equals(e.getGroupId())).count());
    }

    @Test
    void closingBeforeAttentionIsRejectedAndTheGroupStaysApproved() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);

        RuleViolationException ex = assertThrows(RuleViolationException.class,
            () -> closeService.close(close(emergencyId)));

        assertEquals("ATTENTION_NOT_STARTED", ex.code());
        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
        assertEquals(1, reload(group).getOpenMarker());
        assertEquals(EmergencyStatus.ACTIVE, emergencyRepository.findById(emergencyId).orElseThrow().getStatus());
    }

    @Test
    void aRejectedCloseWithoutOutcomeLeavesTheGroupApproved() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);
        attentionService.startAttention(emergencyId);

        assertThrows(RuleViolationException.class,
            () -> closeService.close(new CloseEmergencyCommand(emergencyId, " ", null)));

        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
        assertEquals(EmergencyStatus.IN_PROGRESS, emergencyRepository.findById(emergencyId).orElseThrow().getStatus());
    }

    @Test
    void closingTwiceIsStillRejectedAndTheGroupStaysClosed() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);
        attentionService.startAttention(emergencyId);
        CloseEmergencyResult first = closeService.close(close(emergencyId));

        ConflictException ex = assertThrows(ConflictException.class,
            () -> closeService.close(new CloseEmergencyCommand(emergencyId, "Other", null)));

        assertEquals("INVALID_TRANSITION", ex.code());
        assertEquals(GroupStatus.CLOSED, reload(group).getStatus());
        Emergency stored = emergencyRepository.findById(emergencyId).orElseThrow();
        assertEquals("Resolved", stored.getOutcome());
        assertEquals(first.closedAt(), stored.getClosedAt());
    }

    @Test
    void aSupervisorOfAnotherCompanyCannotCloseAndTheGroupIsUntouched() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        String emergencyId = approvedEmergencyId(group);
        attentionService.startAttention(emergencyId);
        asSupervisor(company.getId() + 1_000_000);

        assertThrows(ForbiddenOperationException.class, () -> closeService.close(close(emergencyId)));

        assertEquals(GroupStatus.APPROVED, reload(group).getStatus());
    }

    @Test
    void closingADriverEmergencyHasNoGroupToClose() {
        Emergency driverEmergency = emergencyRepository.save(Emergency.activateByDriver(
            UUID.randomUUID().toString(), company.getId(), driver.getId(), bus.getId(), shift.getId(),
            route.getId(), null, clock.instant().minusSeconds(30), clock));
        PassengerRequestGroup openGroup = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        attentionService.startAttention(driverEmergency.getId());

        closeService.close(close(driverEmergency.getId()));

        assertEquals(EmergencyStatus.CLOSED,
            emergencyRepository.findById(driverEmergency.getId()).orElseThrow().getStatus());
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(openGroup).getStatus());
    }
}
