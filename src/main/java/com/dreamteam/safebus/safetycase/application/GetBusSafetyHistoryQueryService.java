package com.dreamteam.safebus.safetycase.application;

public interface GetBusSafetyHistoryQueryService {
    // Malformed ids and a shift other than the one of the active journey are denied like any other bus.
    GetBusSafetyHistoryResult getForBus(String busId, String shiftId);
}
