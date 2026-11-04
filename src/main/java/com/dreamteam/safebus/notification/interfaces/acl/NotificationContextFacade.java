package com.dreamteam.safebus.notification.interfaces.acl;

import com.dreamteam.safebus.notification.application.NotificationDispatcher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class NotificationContextFacade {

    public static final String EMERGENCY_ACTIVATED = "EmergencyActivated";
    public static final String PASSENGER_REVIEW_REQUIRED = "PassengerReviewRequired";
    public static final String REVIEW_PRIORITY = "REVIEW";

    /** companyId only routes the event; it is not part of what subscribers receive. */
    public record SupervisorEvent(Long companyId, String name, String id, Object payload) {}

    public record BusContext(Long busId, String plate) {}

    public record ShiftContext(Long shiftId, Long routeId, String routeName) {}

    public record LocationContext(double latitude, double longitude) {}

    public record EmergencyActivatedPayload(String eventId, String type, String priority, String source,
                                            String reference, String groupReference, Instant occurredAt,
                                            BusContext bus, ShiftContext shift, LocationContext location) {}

    public record PassengerReviewRequiredPayload(String eventId, String type, String priority,
                                                 String reference, Instant occurredAt, BusContext bus,
                                                 ShiftContext shift, int contributorCount,
                                                 int requestCount) {}

    private final NotificationDispatcher dispatcher;

    public NotificationContextFacade(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Never throws and never blocks on subscribers. The loader runs on a delivery thread, so it must
     * read committed data and open its own transaction.
     */
    public void publish(Supplier<Optional<SupervisorEvent>> loader) {
        dispatcher.publish(loader);
    }
}
