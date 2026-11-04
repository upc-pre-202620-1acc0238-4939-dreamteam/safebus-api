package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.interfaces.rest.CountingSourceCredentialResource;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CountingSourceCredentialRedactionTest {

    private static final String KEY = "sbk_top_secret_value";

    @Test
    void theResultNeverPrintsTheKey() {
        var result = new CountingSourceCredentialResult(1L, 2L, "door", "ACTIVE", Instant.EPOCH, null, KEY);

        assertFalse(result.toString().contains(KEY));
    }

    @Test
    void theResourceNeverPrintsTheKey() {
        var resource = new CountingSourceCredentialResource(1L, 2L, "door", "ACTIVE", Instant.EPOCH, null, KEY);

        assertFalse(resource.toString().contains(KEY));
    }
}
