package com.dreamteam.safebus.fleetmonitoring.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade.BusSummary;
import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade.DriverSummary;
import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade.RouteSummary;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.BusOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.DriverOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.EmergencyOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.LocationOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.RouteOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.ShiftOverview;
import com.dreamteam.safebus.fleetmonitoring.domain.LocationFreshness;
import com.dreamteam.safebus.fleetmonitoring.domain.LocationStatus;
import com.dreamteam.safebus.safetycase.interfaces.acl.SafetycaseContextFacade;
import com.dreamteam.safebus.safetycase.interfaces.acl.SafetycaseContextFacade.OpenEmergencyView;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade.ActiveShiftView;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade.BusPositionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class GetFleetOverviewQueryServiceImpl implements GetFleetOverviewQueryService {

    // receivedAt descending, then emergency id ascending, so the order never depends on the database
    private static final Comparator<OpenEmergencyView> EMERGENCY_ORDER = Comparator
        .comparing(OpenEmergencyView::receivedAt, Comparator.reverseOrder())
        .thenComparing(OpenEmergencyView::emergencyId);

    private final CurrentUserProvider currentUserProvider;
    private final FleetContextFacade fleetFacade;
    private final TripContextFacade tripFacade;
    private final SafetycaseContextFacade safetycaseFacade;
    private final Clock clock;

    public GetFleetOverviewQueryServiceImpl(CurrentUserProvider currentUserProvider,
                                            FleetContextFacade fleetFacade,
                                            TripContextFacade tripFacade,
                                            SafetycaseContextFacade safetycaseFacade,
                                            Clock clock) {
        this.currentUserProvider = currentUserProvider;
        this.fleetFacade = fleetFacade;
        this.tripFacade = tripFacade;
        this.safetycaseFacade = safetycaseFacade;
        this.clock = clock;
    }

    @Override
    public FleetOverviewResult getOverview() {
        Long companyId = currentUserProvider.current().companyId();
        if (companyId == null) {
            throw new ForbiddenOperationException("FLEET_ACCESS_DENIED", "access denied");
        }

        List<BusSummary> buses = fleetFacade.listBusesOfCompany(companyId);
        List<Long> busIds = buses.stream().map(BusSummary::busId).toList();

        Map<Long, ActiveShiftView> shifts = tripFacade.findActiveShiftsByBusIds(busIds);
        Map<Long, BusPositionView> positions = tripFacade.findLastPositionsByBusIds(busIds);
        Set<Long> driverIds = shifts.values().stream().map(ActiveShiftView::driverId).collect(Collectors.toSet());
        Set<Long> routeIds = shifts.values().stream().map(ActiveShiftView::routeId).collect(Collectors.toSet());
        Map<Long, DriverSummary> drivers = fleetFacade.findDriversByIds(driverIds);
        Map<Long, RouteSummary> routes = fleetFacade.findRoutesByIds(routeIds);
        Map<Long, List<OpenEmergencyView>> emergenciesByBus = safetycaseFacade
            .findOpenEmergenciesOfCompany(companyId).stream()
            .sorted(EMERGENCY_ORDER)
            .collect(Collectors.groupingBy(OpenEmergencyView::busId));

        Instant now = Instant.now(clock);
        List<BusOverview> overview = buses.stream()
            .filter(bus -> bus.enabled() || emergenciesByBus.containsKey(bus.busId()))
            .map(bus -> toOverview(bus, shifts.get(bus.busId()), positions.get(bus.busId()),
                drivers, routes, emergenciesByBus.getOrDefault(bus.busId(), List.of()), now))
            .toList();
        return new FleetOverviewResult(now, overview);
    }

    private static BusOverview toOverview(BusSummary bus, ActiveShiftView shift, BusPositionView position,
                                          Map<Long, DriverSummary> drivers, Map<Long, RouteSummary> routes,
                                          List<OpenEmergencyView> emergencies, Instant now) {
        ShiftOverview shiftOverview = shift == null ? null
            : new ShiftOverview(shift.shiftId(), shift.startedAt());
        RouteSummary route = shift == null ? null : routes.get(shift.routeId());
        DriverSummary driver = shift == null ? null : drivers.get(shift.driverId());
        return new BusOverview(bus.busId(), bus.plate(), bus.enabled(), bus.capacity(),
            shiftOverview,
            route == null ? null : new RouteOverview(route.name(), route.origin(), route.destination()),
            driver == null ? null : new DriverOverview(driver.fullName()),
            toLocation(position, now),
            emergencies.stream().map(GetFleetOverviewQueryServiceImpl::toEmergency).toList());
    }

    private static LocationOverview toLocation(BusPositionView position, Instant now) {
        if (position == null) {
            return new LocationOverview(LocationStatus.UNAVAILABLE, null, null, null, null);
        }
        return new LocationOverview(LocationFreshness.classify(position.capturedAt(), now),
            position.latitude(), position.longitude(), position.accuracyMeters(), position.capturedAt());
    }

    private static EmergencyOverview toEmergency(OpenEmergencyView e) {
        return new EmergencyOverview(e.emergencyId(), e.source(), e.priority(), e.status(),
            e.shiftId(), e.activatedAt(), e.receivedAt(), e.attentionStartedAt());
    }
}
