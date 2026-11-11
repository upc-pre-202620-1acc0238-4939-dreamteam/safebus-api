package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

public record BusSafetyHistoryResource(Long busId, Long shiftId, List<Item> items, String message) {
    public record Item(String reference, String source, Instant recordedAt, String state, String stateLabel,
                       @JsonInclude(JsonInclude.Include.NON_NULL) Integer requestCount) {}
}
