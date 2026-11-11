package com.dreamteam.safebus.safetycase.interfaces.acl;

import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequest;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.safetycase.support.MutableClock;
import com.dreamteam.safebus.safetycase.support.MutableClockConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.RecordComponent;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(MutableClockConfig.class)
class SafetycaseContextFacadeGroupsTest {

    private static final AtomicLong IDS = new AtomicLong(960_000_000L);

    @Autowired SafetycaseContextFacade facade;
    @Autowired PassengerRequestGroupRepository groupRepository;
    @Autowired PassengerRequestRepository requestRepository;
    @Autowired EmergencyRepository emergencyRepository;
    @Autowired MutableClock clock;

    private long company;
    private final List<PassengerRequestGroup> groups = new ArrayList<>();
    private final List<PassengerRequest> requests = new ArrayList<>();
    private final List<Emergency> emergencies = new ArrayList<>();

    @BeforeEach
    void setUp() {
        clock.set(MutableClockConfig.START);
        company = IDS.incrementAndGet();
    }

    @AfterEach
    void cleanUp() {
        emergencyRepository.deleteAllById(emergencies.stream().map(Emergency::getId).toList());
        requestRepository.deleteAllById(requests.stream().map(PassengerRequest::getId).toList());
        groupRepository.deleteAllById(groups.stream().map(PassengerRequestGroup::getId).toList());
    }

    private PassengerRequestGroup group(long companyId, long busId) {
        PassengerRequestGroup g = groupRepository.save(
            PassengerRequestGroup.open(companyId, busId, IDS.incrementAndGet(), clock.instant()));
        groups.add(g);
        return g;
    }

    private void request(PassengerRequestGroup g, RequestStatus status) {
        requests.add(requestRepository.save(PassengerRequest.create(UUID.randomUUID().toString(), g.getId(),
            IDS.incrementAndGet(), 1L, g.getBusId(), g.getShiftId(), g.getCompanyId(), "private message",
            60L, "a".repeat(64), clock.instant(), clock.instant(), status)));
    }

    private PassengerRequestGroup reload(PassengerRequestGroup g) {
        return groupRepository.findById(g.getId()).orElseThrow();
    }

    @Test
    void aCollectingGroupIsOpenWithItsRequestCountAndWindow() {
        PassengerRequestGroup g = group(company, 11L);
        request(g, RequestStatus.COUNTED);
        request(g, RequestStatus.COUNTED);

        List<SafetycaseContextFacade.OpenGroupView> result = facade.findOpenGroupsOfCompany(company);

        assertEquals(List.of(new SafetycaseContextFacade.OpenGroupView(11L, "COLLECTING", 2, 0,
            g.getWindowStartedAt(), g.getWindowExpiresAt(), null, null)), result);
    }

    @Test
    void onlyCountedRequestsAreContributorsAndAttachedOnesAreCountedApart() {
        PassengerRequestGroup g = group(company, 11L);
        request(g, RequestStatus.COUNTED);
        request(g, RequestStatus.COUNTED);
        request(g, RequestStatus.COUNTED);
        request(g, RequestStatus.ATTACHED);
        request(g, RequestStatus.ATTACHED);

        var view = facade.findOpenGroupsOfCompany(company).get(0);

        assertEquals(3, view.requestCount());
        assertEquals(2, view.attachedCount());
    }

    @Test
    void anAwaitingGroupCarriesTheTimeItStartedWaiting() {
        PassengerRequestGroup g = group(company, 11L);
        request(g, RequestStatus.COUNTED);
        clock.advance(Duration.ofMinutes(1));
        PassengerRequestGroup stored = reload(g);
        stored.moveToAwaitingApproval(clock.instant());
        groupRepository.save(stored);

        var view = facade.findOpenGroupsOfCompany(company).get(0);

        assertEquals("AWAITING_APPROVAL", view.status());
        assertEquals(clock.instant(), view.awaitingSince());
        assertNull(view.emergencyStatus());
    }

    @Test
    void anApprovedGroupShowsTheStatusOfItsEmergency() {
        PassengerRequestGroup g = group(company, 11L);
        PassengerRequestGroup stored = reload(g);
        stored.moveToAwaitingApproval(clock.instant());
        stored.approve(5L, clock.instant());
        groupRepository.save(stored);
        emergencies.add(emergencyRepository.save(Emergency.activateByPassengerGroup(UUID.randomUUID().toString(),
            company, 11L, g.getShiftId(), 1L, g.getId(), clock)));

        var view = facade.findOpenGroupsOfCompany(company).get(0);

        assertEquals("APPROVED", view.status());
        assertEquals("ACTIVE", view.emergencyStatus());
    }

    @Test
    void closedDeclinedAndExpiredGroupsAreNotOpen() {
        PassengerRequestGroup declined = group(company, 11L);
        PassengerRequestGroup d = reload(declined);
        d.moveToAwaitingApproval(clock.instant());
        d.decline(5L, "not enough", null, clock.instant());
        groupRepository.save(d);

        PassengerRequestGroup closed = group(company, 12L);
        PassengerRequestGroup c = reload(closed);
        c.moveToAwaitingApproval(clock.instant());
        c.approve(5L, clock.instant());
        c.close();
        groupRepository.save(c);

        PassengerRequestGroup expired = group(company, 13L);
        PassengerRequestGroup e = reload(expired);
        e.expire();
        groupRepository.save(e);

        assertTrue(facade.findOpenGroupsOfCompany(company).isEmpty());
    }

    @Test
    void aCollectingGroupPastItsWindowIsHiddenButAnAwaitingOneIsNot() {
        PassengerRequestGroup collecting = group(company, 11L);
        PassengerRequestGroup awaiting = group(company, 12L);
        PassengerRequestGroup stored = reload(awaiting);
        stored.moveToAwaitingApproval(clock.instant());
        groupRepository.save(stored);

        clock.advance(Duration.ofMinutes(5).minusSeconds(1));
        assertEquals(2, facade.findOpenGroupsOfCompany(company).size());

        clock.advance(Duration.ofSeconds(1));
        var result = facade.findOpenGroupsOfCompany(company);
        assertEquals(1, result.size());
        assertEquals(12L, result.get(0).busId());
        assertEquals(collecting.getWindowExpiresAt(), clock.instant());
    }

    @Test
    void onlyTheGroupsOfTheCompanyAreReturnedOldestWindowFirst() {
        PassengerRequestGroup first = group(company, 11L);
        clock.advance(Duration.ofSeconds(30));
        PassengerRequestGroup second = group(company, 12L);
        group(IDS.incrementAndGet(), 13L);

        var result = facade.findOpenGroupsOfCompany(company);

        assertEquals(List.of(11L, 12L), result.stream().map(SafetycaseContextFacade.OpenGroupView::busId).toList());
        assertEquals(first.getWindowStartedAt(), result.get(0).windowStartedAt());
        assertEquals(second.getWindowStartedAt(), result.get(1).windowStartedAt());
    }

    @Test
    void theViewCarriesNoIdentityEvidenceOrMessage() {
        List<String> names = Arrays.stream(SafetycaseContextFacade.OpenGroupView.class.getRecordComponents())
            .map(RecordComponent::getName).map(String::toLowerCase).toList();

        for (String forbidden : List.of("id", "groupid", "message", "photo", "evidence", "dni", "passengeruserid",
                "journeyid", "publicreference", "userid", "decisionreason")) {
            assertFalse(names.contains(forbidden), forbidden + " in " + names);
        }
        assertTrue(names.contains("busid") || names.contains("status"));
    }
}
