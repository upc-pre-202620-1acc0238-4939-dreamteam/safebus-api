package com.dreamteam.safebus.safetycase.infrastructure.notification;

import com.dreamteam.safebus.notification.interfaces.acl.NotificationContextFacade;
import com.dreamteam.safebus.safetycase.application.SupervisorEventBuilder;
import com.dreamteam.safebus.safetycase.application.SupervisorNotificationRequested;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Hands the event to the notification context only after the surrounding transaction commits. Without a
 * transaction the data is already committed, so the event is forwarded immediately. A rollback sends nothing.
 */
@Component
public class SupervisorEventRelay {

    private final NotificationContextFacade notificationFacade;
    private final SupervisorEventBuilder builder;

    public SupervisorEventRelay(NotificationContextFacade notificationFacade, SupervisorEventBuilder builder) {
        this.notificationFacade = notificationFacade;
        this.builder = builder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onRequested(SupervisorNotificationRequested request) {
        notificationFacade.publish(() -> builder.build(request));
    }
}
