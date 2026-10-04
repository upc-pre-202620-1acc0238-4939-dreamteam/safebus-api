package com.dreamteam.safebus.fleet.interfaces.rest;

import java.time.Instant;

public record DriverResource(
    Long id,
    String fullName,
    String loginId,
    String qrCredential,
    Instant qrCredentialExpiresAt
) {}
