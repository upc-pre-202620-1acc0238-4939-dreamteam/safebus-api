package com.dreamteam.safebus.shared.application;

public record AuthenticatedUser(Long userId, String role, Long companyId) {
}
