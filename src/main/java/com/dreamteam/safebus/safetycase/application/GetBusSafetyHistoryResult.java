package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;
import java.util.List;

public record GetBusSafetyHistoryResult(Long busId, Long shiftId, List<Item> items, String message) {
    public GetBusSafetyHistoryResult {
        items = List.copyOf(items);
    }

    public record Item(String reference, String source, Instant recordedAt, String state) {}
}
