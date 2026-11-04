package com.dreamteam.safebus.notification.infrastructure;

import com.dreamteam.safebus.notification.application.NotificationDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "safebus.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationKeepAliveJob {

    private final NotificationDispatcher dispatcher;

    public NotificationKeepAliveJob(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${safebus.notifications.keep-alive:PT15S}",
        initialDelayString = "${safebus.notifications.keep-alive:PT15S}")
    public void run() {
        dispatcher.sendKeepAlive();
    }
}
