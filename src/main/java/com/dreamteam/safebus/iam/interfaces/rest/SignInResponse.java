package com.dreamteam.safebus.iam.interfaces.rest;

import java.time.Instant;

public record SignInResponse(String accessToken, String tokenType, String role, Instant expiresAt) {
}
