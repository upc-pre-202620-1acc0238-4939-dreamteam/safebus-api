package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.BusContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.EmergencyActivatedPayload;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.LocationContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.ShiftContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Runs on a delivery thread after commit: reads committed rows in its own short transaction. */
@Component
@Transactional(readOnly = true)
public class SupervisorEventBuilder {

    private static final String SOURCE_DRIVER = "DRIVER";

    private final EmergencyRepository emergencyRepository;
    private final FleetContextFacade fleetFacade;

    public SupervisorEventBuilder(EmergencyRepository emergencyRepository, FleetContextFacade fleetFacade) {
        this.emergencyRepository = emergencyRepository;
        this.fleetFacade = fleetFacade;
    }

    public Optional<SupervisorEvent> build(SupervisorNotificationRequested request) {
        return switch (request.kind()) {
            case DRIVER_EMERGENCY -> emergencyRepository.findById(request.emergencyId())
                .map(this::emergencyActivated);
        };
    }

    private SupervisorEvent emergencyActivated(Emergency emergency) {
        EmergencyActivatedPayload payload = new EmergencyActivatedPayload(emergency.getId(),
            NotificationContextFacade.EMERGENCY_ACTIVATED, emergency.getPriority().name(), SOURCE_DRIVER,
            PublicReference.of(emergency.getId()), null, emergency.getActivatedAt(),
            busContext(emergency), shiftContext(emergency), locationContext(emergency));
        return new SupervisorEvent(emergency.getCompanyId(), NotificationContextFacade.EMERGENCY_ACTIVATED,
            emergency.getId(), payload);
    }

    private BusContext busContext(Emergency emergency) {
        String plate = fleetFacade.listBusesOfCompany(emergency.getCompanyId()).stream()
            .filter(bus -> bus.busId().equals(emergency.getBusId()))
            .map(FleetContextFacade.BusSummary::plate)
            .findFirst().orElse(null);
        return new BusContext(emergency.getBusId(), plate);
    }

    private ShiftContext shiftContext(Emergency emergency) {
        String routeName = Optional.ofNullable(emergency.getRouteId())
            .map(id -> fleetFacade.findRoutesByIds(Set.of(id)).get(id))
            .map(FleetContextFacade.RouteSummary::name)
            .orElse(null);
        return new ShiftContext(emergency.getShiftId(), emergency.getRouteId(), routeName);
    }

    private static LocationContext locationContext(Emergency emergency) {
        if (emergency.getPoint() == null) {
            return null;
        }
        return new LocationContext(emergency.getPoint().getLatitude(), emergency.getPoint().getLongitude());
    }
}
