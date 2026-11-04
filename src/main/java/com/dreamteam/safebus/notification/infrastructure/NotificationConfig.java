package com.dreamteam.safebus.notification.infrastructure;

import com.dreamteam.safebus.notification.application.NotificationDispatcher;
import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public SubscriptionRegistry subscriptionRegistry(NotificationProperties properties) {
        return new SubscriptionRegistry(properties.maxPerUser(), properties.maxPerCompany(),
            properties.mailboxCapacity());
    }

    // Built here and not exposed as a bean: an Executor bean would switch off Boot's applicationTaskExecutor.
    @Bean(destroyMethod = "shutdown")
    public NotificationDispatcher notificationDispatcher(SubscriptionRegistry registry,
                                                         NotificationProperties properties) {
        AtomicInteger counter = new AtomicInteger();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(properties.deliveryThreads(),
            properties.deliveryThreads(), 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(properties.queueCapacity()),
            task -> {
                Thread thread = new Thread(task, "notification-delivery-" + counter.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
        return new NotificationDispatcher(registry, executor);
    }
}
