package com.dreamteam.safebus.shared.infrastructure;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DevProfileGuardTest {

    @Test
    void verifyNotRunningInAzure_blankSiteName_doesNotThrow() {
        assertDoesNotThrow(new DevProfileGuard("")::verifyNotRunningInAzure);
        assertDoesNotThrow(new DevProfileGuard("   ")::verifyNotRunningInAzure);
    }

    @Test
    void verifyNotRunningInAzure_siteNamePresent_failsStartup() {
        DevProfileGuard guard = new DevProfileGuard("safebus-api");

        IllegalStateException ex = assertThrows(IllegalStateException.class, guard::verifyNotRunningInAzure);
        assertEquals("dev profile is active inside Azure App Service; set SPRING_PROFILES_ACTIVE=prod",
                ex.getMessage());
    }
}
