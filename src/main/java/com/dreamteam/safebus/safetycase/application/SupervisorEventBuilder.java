package com.dreamteam.safebus.safetycase.application;

import com.dreamteam.safebus.fleet.interfaces.acl.FleetContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.BusContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.EmergencyActivatedPayload;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.LocationContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.PassengerReviewRequiredPayload;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.ShiftContext;
import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade.SupervisorEvent;
import com.dreamteam.safebus.safetycase.domain.model.Emergency;
import com.dreamteam.safebus.safetycase.domain.model.EmergencySource;
import com.dreamteam.safebus.safetycase.domain.model.PassengerRequestGroup;
import com.dreamteam.safebus.safetycase.domain.model.RequestStatus;
import com.dreamteam.safebus.safetycase.domain.repository.EmergencyRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestGroupRepository;
import com.dreamteam.safebus.safetycase.domain.repository.PassengerRequestRepository;
import com.dreamteam.safebus.trip.interfaces.acl.TripContextFacade;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/** Runs on a delivery thread after commit: reads committed rows in its own short transaction. */
@Component
@Transactional(readOnly = true)
public class SupervisorEventBuilder {

    private final EmergencyRepository emergencyRepository;
    private final PassengerRequestGroupRepository groupRepository;
    private final PassengerRequestRepository requestRepository;
    private final FleetContextFacade fleetFacade;
    private final TripContextFacade tripFacade;

    public SupervisorEventBuilder(EmergencyRepository emergencyRepository,
                                  PassengerRequestGroupRepository groupRepository,
                                  PassengerRequestRepository requestRepository, FleetContextFacade fleetFacade,
                                  TripContextFacade tripFacade) {
        this.emergencyRepository = emergencyRepository;
        this.groupRepository = groupRepository;
        this.requestRepository = requestRepository;
        this.fleetFacade = fleetFacade;
        this.tripFacade = tripFacade;
    }

    public Optional<SupervisorEvent> build(SupervisorNotificationRequested request) {
        return switch (request.kind()) {
            case DRIVER_EMERGENCY -> emergencyRepository.findById(request.emergencyId())
                .map(emergency -> emergencyActivated(emergency, null));
            case GROUP_APPROVED -> emergencyRepository.findById(request.emergencyId())
                .flatMap(emergency -> groupRepository.findById(request.groupId())
                    .map(group -> emergencyActivated(emergency,
                        PublicReference.groupRecordId(group.getId(), group.getPublicReference()))));
            case GROUP_REVIEW_REQUIRED -> groupRepository.findById(request.groupId())
                .flatMap(this::reviewRequired);
        };
    }

    private SupervisorEvent emergencyActivated(Emergency emergency, String groupReference) {
        EmergencyActivatedPayload payload = new EmergencyActivatedPayload(emergency.getId(),
            NotificationContextFacade.EMERGENCY_ACTIVATED, emergency.getPriority().name(),
            emergency.getSource() == EmergencySource.DRIVER ? "DRIVER" : "PASSENGER_GROUP",
            PublicReference.of(emergency.getId()), groupReference, emergency.getActivatedAt(),
            busContext(emergency.getCompanyId(), emergency.getBusId()),
            shiftContext(emergency.getShiftId(), emergency.getRouteId()), locationContext(emergency));
        return new SupervisorEvent(emergency.getCompanyId(), NotificationContextFacade.EMERGENCY_ACTIVATED,
            emergency.getId(), payload);
    }

    private Optional<SupervisorEvent> reviewRequired(PassengerRequestGroup group) {
        Instant occurredAt = group.getAwaitingSince();
        if (occurredAt == null) {
            return Optional.empty();
        }
        String eventId = PublicReference.groupRecordId(group.getId(), group.getPublicReference());
        Long routeId = tripFacade.findShiftById(group.getShiftId())
            .map(TripContextFacade.ShiftInfo::routeId).orElse(null);
        PassengerReviewRequiredPayload payload = new PassengerReviewRequiredPayload(eventId,
            NotificationContextFacade.PASSENGER_REVIEW_REQUIRED, NotificationContextFacade.REVIEW_PRIORITY,
            PublicReference.ofGroup(group.getId(), group.getPublicReference()), occurredAt,
            busContext(group.getCompanyId(), group.getBusId()), shiftContext(group.getShiftId(), routeId),
            (int) requestRepository.countByGroupIdAndStatus(group.getId(), RequestStatus.COUNTED),
            (int) requestRepository.countByGroupId(group.getId()));
        return Optional.of(new SupervisorEvent(group.getCompanyId(),
            NotificationContextFacade.PASSENGER_REVIEW_REQUIRED, eventId, payload));
    }

    private BusContext busContext(Long companyId, Long busId) {
        String plate = fleetFacade.listBusesOfCompany(companyId).stream()
            .filter(bus -> bus.busId().equals(busId))
            .map(FleetContextFacade.BusSummary::plate)
            .findFirst().orElse(null);
        return new BusContext(busId, plate);
    }

    private ShiftContext shiftContext(Long shiftId, Long routeId) {
        String routeName = Optional.ofNullable(routeId)
            .map(id -> fleetFacade.findRoutesByIds(Set.of(id)).get(id))
            .map(FleetContextFacade.RouteSummary::name)
            .orElse(null);
        return new ShiftContext(shiftId, routeId, routeName);
    }

    private static LocationContext locationContext(Emergency emergency) {
        if (emergency.getPoint() == null) {
            return null;
        }
        return new LocationContext(emergency.getPoint().getLatitude(), emergency.getPoint().getLongitude());
    }
}
