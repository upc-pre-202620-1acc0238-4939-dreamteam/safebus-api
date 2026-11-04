package com.dreamteam.safebus.notification.application;

import com.dreamteam.safebus.shared.domain.exceptions.TooManyRequestsException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** In-memory and per instance: a subscriber only receives events published by the instance it is connected to. */
public class SubscriptionRegistry {

    private final int maxPerUser;
    private final int maxPerCompany;
    private final int mailboxCapacity;
    private final Map<Long, List<NotificationSubscription>> byCompany = new HashMap<>();

    public SubscriptionRegistry(int maxPerUser, int maxPerCompany, int mailboxCapacity) {
        this.maxPerUser = maxPerUser;
        this.maxPerCompany = maxPerCompany;
        this.mailboxCapacity = mailboxCapacity;
    }

    public synchronized NotificationSubscription register(Long userId, Long companyId, EventSink sink) {
        List<NotificationSubscription> current = byCompany.computeIfAbsent(companyId, id -> new ArrayList<>());
        current.removeIf(NotificationSubscription::isClosed);
        if (current.size() >= maxPerCompany) {
            throw new TooManyRequestsException("SUBSCRIPTION_LIMIT_COMPANY",
                "the company reached its limit of " + maxPerCompany + " open notification streams");
        }
        if (current.stream().filter(s -> s.userId().equals(userId)).count() >= maxPerUser) {
            throw new TooManyRequestsException("SUBSCRIPTION_LIMIT_USER",
                "this account reached its limit of " + maxPerUser + " open notification streams");
        }
        NotificationSubscription subscription =
            new NotificationSubscription(userId, companyId, sink, mailboxCapacity);
        current.add(subscription);
        return subscription;
    }

    public synchronized void remove(NotificationSubscription subscription) {
        List<NotificationSubscription> current = byCompany.get(subscription.companyId());
        if (current != null) {
            current.remove(subscription);
            if (current.isEmpty()) {
                byCompany.remove(subscription.companyId());
            }
        }
        subscription.close();
    }

    public synchronized List<NotificationSubscription> ofCompany(Long companyId) {
        return List.copyOf(byCompany.getOrDefault(companyId, List.of()));
    }

    public synchronized List<NotificationSubscription> all() {
        return byCompany.values().stream().flatMap(List::stream).toList();
    }

    public synchronized int count(Long companyId) {
        return byCompany.getOrDefault(companyId, List.of()).size();
    }
}
