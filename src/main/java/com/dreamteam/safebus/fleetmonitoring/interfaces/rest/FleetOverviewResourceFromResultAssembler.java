package com.dreamteam.safebus.fleetmonitoring.interfaces.rest;

import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.BusOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.EmergencyOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.LocationOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.OccupancyOverview;
import com.dreamteam.safebus.fleetmonitoring.application.FleetOverviewResult.PassengerGroupOverview;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.BusResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.DriverResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.EmergencyResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.GroupEmergencyResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.LocationResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.OccupancyResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.PassengerGroupResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.RouteResource;
import com.dreamteam.safebus.fleetmonitoring.interfaces.rest.FleetOverviewResource.ShiftResource;

public final class FleetOverviewResourceFromResultAssembler {

    private FleetOverviewResourceFromResultAssembler() {}

    public static FleetOverviewResource toResource(FleetOverviewResult result) {
        return new FleetOverviewResource(result.generatedAt(),
            result.buses().stream().map(FleetOverviewResourceFromResultAssembler::toBus).toList());
    }

    private static BusResource toBus(BusOverview bus) {
        return new BusResource(bus.busId(), bus.plate(), bus.enabled(), bus.capacity(),
            bus.shift() == null ? null : new ShiftResource(bus.shift().shiftId(), bus.shift().startedAt()),
            bus.route() == null ? null
                : new RouteResource(bus.route().name(), bus.route().origin(), bus.route().destination()),
            bus.driver() == null ? null : new DriverResource(bus.driver().fullName()),
            toLocation(bus.location()),
            toOccupancy(bus.occupancy()),
            bus.emergencies().stream().map(FleetOverviewResourceFromResultAssembler::toEmergency).toList(),
            bus.passengerGroups().stream().map(FleetOverviewResourceFromResultAssembler::toGroup).toList());
    }

    private static OccupancyResource toOccupancy(OccupancyOverview o) {
        return new OccupancyResource(o.status(), o.reason(), o.count(), o.capacity(), o.load(),
            o.lastCountAt(), o.lastHeartbeatAt());
    }

    private static PassengerGroupResource toGroup(PassengerGroupOverview g) {
        return new PassengerGroupResource(g.status(), g.requestCount(), g.windowStartedAt(), g.windowExpiresAt(),
            g.awaitingSince(), g.emergencyStatus() == null ? null : new GroupEmergencyResource(g.emergencyStatus()));
    }

    private static LocationResource toLocation(LocationOverview location) {
        return new LocationResource(location.status().name(), location.latitude(), location.longitude(),
            location.accuracyMeters(), location.capturedAt());
    }

    private static EmergencyResource toEmergency(EmergencyOverview e) {
        return new EmergencyResource(e.id(), e.source(), e.priority(), e.status(), e.shiftId(),
            e.activatedAt(), e.receivedAt(), e.attentionStartedAt());
    }
}
