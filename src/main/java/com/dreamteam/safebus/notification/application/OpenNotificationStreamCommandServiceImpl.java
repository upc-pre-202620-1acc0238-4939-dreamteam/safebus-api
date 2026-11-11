package com.dreamteam.safebus.notification.application;

import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import com.dreamteam.safebus.shared.domain.exceptions.ForbiddenOperationException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

public class OpenNotificationStreamCommandServiceImpl implements OpenNotificationStreamCommandService {

    private static final Duration MIN_LIFETIME = Duration.ofSeconds(1);
    private static final String SCOPE_DENIED = "NOTIFICATION_SCOPE_DENIED";

    private final SubscriptionRegistry registry;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;
    private final Duration maxConnectionAge;

    public OpenNotificationStreamCommandServiceImpl(SubscriptionRegistry registry,
                                                    CurrentUserProvider currentUserProvider, Clock clock,
                                                    Duration maxConnectionAge) {
        this.registry = registry;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
        this.maxConnectionAge = maxConnectionAge;
    }

    @Override
    public StreamTicket authorize(OpenNotificationStreamCommand command) {
        AuthenticatedUser caller = currentUserProvider.current();
        Long tokenCompany = caller.companyId();
        if (tokenCompany == null) {
            throw new ForbiddenOperationException(SCOPE_DENIED, "the token carries no company");
        }
        if (command.requestedCompanyId() != null && !command.requestedCompanyId().equals(tokenCompany)) {
            throw new ForbiddenOperationException(SCOPE_DENIED,
                "notifications are only available for the company of the token");
        }
        return new StreamTicket(caller.userId(), tokenCompany, lifetime(command.tokenExpiresAt()));
    }

    @Override
    public NotificationSubscription subscribe(StreamTicket ticket, EventSink sink) {
        return registry.register(ticket.userId(), ticket.companyId(), sink);
    }

    @Override
    public void unsubscribe(NotificationSubscription subscription) {
        registry.remove(subscription);
    }

    // A stream never outlives its token: the client reconnects, authenticates again and reconciles through REST
    private Duration lifetime(Instant tokenExpiresAt) {
        if (tokenExpiresAt == null) {
            return maxConnectionAge;
        }
        Duration untilExpiry = Duration.between(Instant.now(clock), tokenExpiresAt);
        Duration lifetime = untilExpiry.compareTo(maxConnectionAge) < 0 ? untilExpiry : maxConnectionAge;
        return lifetime.compareTo(MIN_LIFETIME) < 0 ? MIN_LIFETIME : lifetime;
    }
}
