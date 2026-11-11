package com.dreamteam.safebus.shared.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Set;

// The only routes where a counting-source key is accepted
public final class DeviceRoutes {

    public static final String[] PATHS = {
        "/api/v1/occupancy-initializations",
        "/api/v1/occupancy-events",
        "/api/v1/occupancy-heartbeats"
    };

    private static final Set<String> PATH_SET = Set.of(PATHS);

    private DeviceRoutes() {}

    public static boolean matches(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && PATH_SET.contains(request.getRequestURI());
    }
}
