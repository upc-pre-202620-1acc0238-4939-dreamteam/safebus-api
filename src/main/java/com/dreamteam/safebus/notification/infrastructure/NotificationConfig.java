package com.dreamteam.safebus.notification.infrastructure;

import com.dreamteam.safebus.notification.application.SubscriptionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationConfig {

    @Bean
    public SubscriptionRegistry subscriptionRegistry(NotificationProperties properties) {
        return new SubscriptionRegistry(properties.maxPerUser(), properties.maxPerCompany(),
            properties.mailboxCapacity());
    }
}
