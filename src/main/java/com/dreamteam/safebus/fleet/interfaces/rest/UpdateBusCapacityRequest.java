package com.dreamteam.safebus.fleet.interfaces.rest;

import tools.jackson.databind.annotation.JsonDeserialize;

import java.math.BigDecimal;

public record UpdateBusCapacityRequest(
    @JsonDeserialize(as = BigDecimal.class)
    Number capacity,
    String technicalRecordReference
) {}
