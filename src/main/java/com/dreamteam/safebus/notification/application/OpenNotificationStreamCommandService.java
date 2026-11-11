package com.dreamteam.safebus.notification.application;

public interface OpenNotificationStreamCommandService {

    /** Resolves the company from the token; a company named by the client is only compared, never trusted. */
    StreamTicket authorize(OpenNotificationStreamCommand command);

    NotificationSubscription subscribe(StreamTicket ticket, EventSink sink);

    void unsubscribe(NotificationSubscription subscription);
}
