package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.passenger.domain.model.PassengerJourney;
import com.dreamteam.safebus.safetycase.domain.model.GroupStatus;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.support.AbstractPassengerRequestTest;
import com.dreamteam.safebus.safetycase.support.TestImages;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

class ExpireOverdueGroupsTest extends AbstractPassengerRequestTest {

    private static final long U1 = 62001L;
    private static final long U2 = 62002L;

    @Autowired ExpireOverdueGroups expireOverdueGroups;
    @Autowired RegisterPassengerRequest register;
    @MockitoBean CurrentUserProvider currentUser;

    private void submit(long userId) {
        PassengerJourney journey = activeJourney(userId);
        when(currentUser.current()).thenReturn(new AuthenticatedUser(userId, "PASSENGER", null));
        register.register(new RegisterPassengerRequestCommand(UUID.randomUUID().toString(), journey.getId(),
            "Danger on board", clock.instant().minusSeconds(10), TestImages.jpeg(7)));
    }

    @Test
    void expiresACollectingGroupWhoseWindowHasPassedAndKeepsItsRequests() {
        submit(U1);
        submit(U2);
        clock.advance(Duration.ofMinutes(5));

        int expired = expireOverdueGroups.expire();

        assertEquals(1, expired);
        PassengerRequestGroup group = groupsOfShift().getFirst();
        assertEquals(GroupStatus.EXPIRED, group.getStatus());
        assertNull(group.getOpenMarker());
        assertEquals(2, requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED));
        assertEquals(0, emergencyRepository.count());
    }

    @Test
    void leavesAGroupWhoseWindowIsStillOpen() {
        submit(U1);
        clock.advance(Duration.ofMinutes(5).minusMillis(1));

        int expired = expireOverdueGroups.expire();

        assertEquals(0, expired);
        assertEquals(GroupStatus.COLLECTING, groupsOfShift().getFirst().getStatus());
        assertEquals(1, groupsOfShift().getFirst().getOpenMarker());
    }

    @Test
    void neverTouchesAGroupAwaitingApprovalNoMatterHowOldItIs() {
        PassengerRequestGroup group = groupInStatus(GroupStatus.AWAITING_APPROVAL);
        clock.advance(Duration.ofDays(2));

        int expired = expireOverdueGroups.expire();

        assertEquals(0, expired);
        PassengerRequestGroup after = groupRepository.findById(group.getId()).orElseThrow();
        assertEquals(GroupStatus.AWAITING_APPROVAL, after.getStatus());
        assertEquals(1, after.getOpenMarker());
    }

    @Test
    void aSecondRunFindsNothingLeftToExpireAndAllowsANewGroupOnTheShift() {
        submit(U1);
        clock.advance(Duration.ofMinutes(6));
        assertEquals(1, expireOverdueGroups.expire());

        assertEquals(0, expireOverdueGroups.expire());

        submit(U2);
        assertEquals(2, groupsOfShift().size());
        assertEquals(GroupStatus.COLLECTING, groupsOfShift().get(1).getStatus());
    }
}
