package com.dreamteam.safebus.shared.infrastructure;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevProfileGuard {

    private final String azureSiteName;

    public DevProfileGuard(@Value("${WEBSITE_SITE_NAME:}") String azureSiteName) {
        this.azureSiteName = azureSiteName;
    }

    @PostConstruct
    void verifyNotRunningInAzure() {
        if (azureSiteName != null && !azureSiteName.isBlank()) {
            throw new IllegalStateException(
                    "dev profile is active inside Azure App Service; set SPRING_PROFILES_ACTIVE=prod");
        }
    }
}
