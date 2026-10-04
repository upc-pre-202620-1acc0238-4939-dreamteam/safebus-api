package com.dreamteam.safebus.fleet.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateRouteRequest(
    @NotBlank String name,
    @NotBlank String origin,
    @NotBlank String destination
) {}
