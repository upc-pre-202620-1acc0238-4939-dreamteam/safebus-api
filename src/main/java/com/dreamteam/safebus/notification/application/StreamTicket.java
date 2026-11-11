package com.dreamteam.safebus.notification.application;

import java.time.Duration;

public record StreamTicket(Long userId, Long companyId, Duration lifetime) {
}
