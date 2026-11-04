package com.dreamteam.safebus.shared.application;

import java.util.Optional;

// Resolves a raw counting-source key to its source; only active sources authenticate
public interface CountingSourceKeyAuthenticator {
    Optional<CountingSourcePrincipal> authenticate(String rawKey);
}
