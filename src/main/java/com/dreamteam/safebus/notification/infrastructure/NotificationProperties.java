package com.dreamteam.safebus.notification.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "safebus.notifications")
public record NotificationProperties(
    @DefaultValue("8") int deliveryThreads,
    @DefaultValue("1000") int queueCapacity,
    @DefaultValue("64") int mailboxCapacity,
    @DefaultValue("3") int maxPerUser,
    @DefaultValue("50") int maxPerCompany,
    @DefaultValue("PT15S") Duration keepAlive,
    @DefaultValue("PT30M") Duration maxConnectionAge
) {
}
