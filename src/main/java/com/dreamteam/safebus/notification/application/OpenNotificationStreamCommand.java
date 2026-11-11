package com.dreamteam.safebus.notification.application;

import java.time.Instant;

public record OpenNotificationStreamCommand(Long requestedCompanyId, Instant tokenExpiresAt) {
}
