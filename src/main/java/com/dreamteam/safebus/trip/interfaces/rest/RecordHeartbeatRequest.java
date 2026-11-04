package com.dreamteam.safebus.trip.interfaces.rest;

import jakarta.validation.constraints.NotNull;

public record RecordHeartbeatRequest(@NotNull Long busId) {}
