package com.dreamteam.safebus.fleet.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateBusRequest(@NotBlank String plate) {}
