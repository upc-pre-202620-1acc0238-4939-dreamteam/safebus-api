package com.dreamteam.safebus.iam.application;

import java.time.Instant;

public record SignInResult(String accessToken, String role, Instant expiresAt) {
}
