package com.dreamteam.safebus.trip.interfaces.acl;

import com.dreamteam.safebus.fleet.domain.model.Bus;
import com.dreamteam.safebus.trip.domain.model.BusOccupancy;
import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;
import com.dreamteam.safebus.trip.domain.repository.BusOccupancyRepository;
import com.dreamteam.safebus.trip.support.AbstractOccupancyTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TripContextFacadeOccupancyTest extends AbstractOccupancyTest {

    @Autowired TripContextFacade facade;
    @MockitoSpyBean BusOccupancyRepository spiedOccupancyRepository;

    private void initializeWith(int entries) {
        BusOccupancy row = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        row.initialize("init-" + bus.getId(), shift.getId(), 0, clock.instant(), clock.instant());
        for (int i = 1; i <= entries; i++) {
            row.apply(shift.getId(), OccupancyEventType.ENTRY, i, clock.instant());
        }
        row.recordHeartbeat(clock.instant());
        occupancyRepository.saveAndFlush(row);
    }

    @Test
    void aCurrentCountCarriesTheCountCapacityAndLoad() {
        bus.recordCapacity(2, "REF", 1L, clock);
        busRepository.save(bus);
        initializeWith(3);

        TripContextFacade.OccupancyView view = facade.findOccupancyByBusIds(List.of(bus.getId())).get(bus.getId());

        assertEquals(new TripContextFacade.OccupancyView(bus.getId(), "CURRENT", null, 3, 2, "OVER_CAPACITY",
            clock.instant(), clock.instant()), view);
    }

    @Test
    void theCountGoesStaleAfterTwoMinutesWithoutAHeartbeat() {
        initializeWith(1);
        clock.advance(Duration.ofMinutes(2).plusSeconds(1));

        TripContextFacade.OccupancyView view = facade.findOccupancyByBusIds(Set.of(bus.getId())).get(bus.getId());

        assertEquals("STALE", view.status());
        assertEquals(1, view.count());
        assertEquals("CAPACITY_UNKNOWN", view.load());
    }

    @Test
    void everyRequestedBusGetsAViewEvenWithoutAnyCount() {
        Bus other = anotherBusOfTheCompany();
        initializeWith(1);

        Map<Long, TripContextFacade.OccupancyView> views =
            facade.findOccupancyByBusIds(List.of(bus.getId(), other.getId(), 987_654_321L));

        assertEquals(Set.of(bus.getId(), other.getId(), 987_654_321L), views.keySet());
        TripContextFacade.OccupancyView none = views.get(other.getId());
        assertEquals("UNAVAILABLE", none.status());
        assertEquals("NOT_INITIALIZED", none.reason());
        assertNull(none.count());
        assertNull(none.load());
        assertNull(none.lastCountAt());
        assertEquals("UNAVAILABLE", views.get(987_654_321L).status());
    }

    @Test
    void anUnavailableCountNeverExposesTheCount() {
        initializeWith(1);
        BusOccupancy row = occupancyRepository.findByBusId(bus.getId()).orElseThrow();
        row.apply(shift.getId(), OccupancyEventType.ENTRY, 5, clock.instant());
        occupancyRepository.saveAndFlush(row);

        TripContextFacade.OccupancyView view = facade.findOccupancyByBusIds(List.of(bus.getId())).get(bus.getId());

        assertEquals("UNAVAILABLE", view.status());
        assertEquals("SEQUENCE_GAP", view.reason());
        assertNull(view.count());
        assertNull(view.load());
    }

    @Test
    void aCountWhoseShiftIsClosedIsUnavailable() {
        initializeWith(1);
        closeShift(shift);

        TripContextFacade.OccupancyView view = facade.findOccupancyByBusIds(List.of(bus.getId())).get(bus.getId());

        assertEquals("UNAVAILABLE", view.status());
        assertNull(view.count());
    }

    @Test
    void anEmptyOrNullCollectionDoesNotQuery() {
        clearInvocations(spiedOccupancyRepository);

        assertTrue(facade.findOccupancyByBusIds(List.of()).isEmpty());
        assertTrue(facade.findOccupancyByBusIds(null).isEmpty());

        verify(spiedOccupancyRepository, never()).findByBusIdIn(any());
    }
}
