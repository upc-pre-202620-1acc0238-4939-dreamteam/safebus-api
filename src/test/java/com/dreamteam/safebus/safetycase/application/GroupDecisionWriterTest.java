package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyPriority;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.EmergencyStatus;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.shared.domain.exceptions.RuleViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

class GroupDecisionWriterTest extends AbstractPassengerRequestTest {

    private static final long SUPERVISOR = 71001L;
    private static final long OTHER_SUPERVISOR = 71002L;

    @Autowired ApproveGroupCommandService approveService;
    @Autowired DeclineGroupCommandService declineService;
    @MockitoBean CurrentUserProvider currentUser;

    @BeforeEach
    void actAsSupervisorOfTheCompany() {
        as(SUPERVISOR, company.getId());
    }

    private void as(long userId, Long companyId) {
        when(currentUser.current()).thenReturn(new AuthenticatedUser(userId, "SUPERVISOR", companyId));
    }

    private PassengerRequestGroup reload(PassengerRequestGroup group) {
        return groupRepository.findById(group.getId()).orElseThrow();
    }

    private List<Emergency> emergenciesOf(PassengerRequestGroup group) {
        return emergencyRepository.findAll().stream()
            .filter(e -> group.getId().equals(e.getGroupId())).toList();
    }

    @Test
    void approveCreatesExactlyOneHighEmergencyLinkedToTheGroup() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        ApproveGroupResult result = approveService.approve(new ApproveGroupCommand(group.getId()));

        assertFalse(result.replay());
        assertEquals("APPROVED", result.status());
        assertEquals("HIGH", result.priority());
        List<Emergency> emergencies = emergenciesOf(group);
        assertEquals(1, emergencies.size());
        Emergency e = emergencies.get(0);
        assertEquals(result.emergencyId(), e.getId());
        assertDoesNotThrowUuid(e.getId());
        assertEquals(EmergencySource.PASSENGER_GROUP, e.getSource());
        assertEquals(EmergencyPriority.HIGH, e.getPriority());
        assertEquals(EmergencyStatus.ACTIVE, e.getStatus());
        assertEquals(company.getId(), e.getCompanyId());
        assertEquals(bus.getId(), e.getBusId());
        assertEquals(shift.getId(), e.getShiftId());
        assertEquals(route.getId(), e.getRouteId());
        assertNull(e.getDriverId());
        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.APPROVED, stored.getStatus());
        assertEquals(SUPERVISOR, stored.getDecidedByUserId());
        assertEquals(1, stored.getOpenMarker());
        assertEquals(stored.getDecidedAt(), result.approvedAt());
    }

    private void assertDoesNotThrowUuid(String id) {
        assertEquals(id, UUID.fromString(id).toString());
    }

    @Test
    void approvingAgainReturnsTheSameEmergencyWithoutCreatingAnother() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        ApproveGroupResult first = approveService.approve(new ApproveGroupCommand(group.getId()));
        as(OTHER_SUPERVISOR, company.getId());

        ApproveGroupResult second = approveService.approve(new ApproveGroupCommand(group.getId()));

        assertTrue(second.replay());
        assertEquals(first.emergencyId(), second.emergencyId());
        assertEquals(first.approvedAt(), second.approvedAt());
        assertEquals(1, emergenciesOf(group).size());
        assertEquals(SUPERVISOR, reload(group).getDecidedByUserId());
    }

    @Test
    void approveIsRejectedFromEveryStatusExceptAwaitingAndApproved() {
        for (GroupStatus status : List.of(GroupStatus.COLLECTING, GroupStatus.EXPIRED,
                GroupStatus.NOT_APPROVED, GroupStatus.CLOSED)) {
            PassengerRequestGroup group = groupInStatus(status);
            ConflictException ex = assertThrows(ConflictException.class,
                () -> approveService.approve(new ApproveGroupCommand(group.getId())));
            assertEquals("INVALID_TRANSITION", ex.code());
            assertEquals(0, emergenciesOf(group).size());
            assertEquals(status, reload(group).getStatus());
            groupRepository.delete(group);
        }
    }

    @Test
    void aForeignOrMissingGroupGivesTheSameForbiddenError() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        as(SUPERVISOR, company.getId() + 1_000_000);

        ForbiddenOperationException foreign = assertThrows(ForbiddenOperationException.class,
            () -> approveService.approve(new ApproveGroupCommand(group.getId())));
        ForbiddenOperationException missing = assertThrows(ForbiddenOperationException.class,
            () -> approveService.approve(new ApproveGroupCommand(Long.MAX_VALUE)));
        ForbiddenOperationException nullId = assertThrows(ForbiddenOperationException.class,
            () -> approveService.approve(new ApproveGroupCommand(null)));

        assertEquals(foreign.code(), missing.code());
        assertEquals(foreign.getMessage(), missing.getMessage());
        assertEquals(foreign.getMessage(), nullId.getMessage());
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
        assertEquals(0, emergenciesOf(group).size());
    }

    @Test
    void aTokenWithoutCompanyCannotDecide() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        as(SUPERVISOR, null);

        assertThrows(ForbiddenOperationException.class,
            () -> approveService.approve(new ApproveGroupCommand(group.getId())));
        assertThrows(ForbiddenOperationException.class,
            () -> declineService.decline(new DeclineGroupCommand(group.getId(), "reason", null)));
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
    }

    @Test
    void declineMovesTheGroupToNotApprovedAndFreesTheShift() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        DeclineGroupResult result = declineService.decline(
            new DeclineGroupCommand(group.getId(), "  Not credible  ", "  Thanks  "));

        assertEquals("NOT_APPROVED", result.status());
        assertEquals("Not credible", result.reason());
        assertEquals("Thanks", result.response());
        PassengerRequestGroup stored = reload(group);
        assertEquals(GroupStatus.NOT_APPROVED, stored.getStatus());
        assertNull(stored.getOpenMarker());
        assertEquals(SUPERVISOR, stored.getDecidedByUserId());
        assertEquals(0, emergenciesOf(group).size());
    }

    @Test
    void decliningAgainReturnsTheStoredDecisionAndIgnoresTheNewBody() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        DeclineGroupResult first = declineService.decline(
            new DeclineGroupCommand(group.getId(), "First reason", "First response"));
        as(OTHER_SUPERVISOR, company.getId());

        DeclineGroupResult second = declineService.decline(
            new DeclineGroupCommand(group.getId(), "Other reason", null));

        assertEquals(first, second);
        assertEquals(SUPERVISOR, reload(group).getDecidedByUserId());
    }

    @Test
    void declineIsRejectedFromEveryStatusExceptAwaitingAndNotApproved() {
        for (GroupStatus status : List.of(GroupStatus.COLLECTING, GroupStatus.EXPIRED,
                GroupStatus.APPROVED, GroupStatus.CLOSED)) {
            PassengerRequestGroup group = groupInStatus(status);
            ConflictException ex = assertThrows(ConflictException.class,
                () -> declineService.decline(new DeclineGroupCommand(group.getId(), "reason", null)));
            assertEquals("INVALID_TRANSITION", ex.code());
            assertEquals(status, reload(group).getStatus());
            groupRepository.delete(group);
        }
    }

    @Test
    void declineValidatesTheReasonAndLeavesTheGroupAwaiting() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);

        RuleViolationException blank = assertThrows(RuleViolationException.class,
            () -> declineService.decline(new DeclineGroupCommand(group.getId(), "   ", null)));
        RuleViolationException tooLong = assertThrows(RuleViolationException.class,
            () -> declineService.decline(new DeclineGroupCommand(group.getId(), "x".repeat(501), null)));

        assertEquals("REASON_REQUIRED", blank.code());
        assertEquals("REASON_TOO_LONG", tooLong.code());
        assertEquals(GroupStatus.AWAITING_APPROVAL, reload(group).getStatus());
    }

    @Test
    void declineOfAForeignGroupGivesTheSameForbiddenErrorAsApprove() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        as(SUPERVISOR, company.getId() + 1_000_000);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
            () -> declineService.decline(new DeclineGroupCommand(group.getId(), "reason", null)));

        assertEquals("GROUP_ACCESS_DENIED", ex.code());
        assertNotEquals(GroupStatus.NOT_APPROVED, reload(group).getStatus());
    }
}
