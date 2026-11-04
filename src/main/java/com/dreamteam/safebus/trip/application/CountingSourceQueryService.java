package com.dreamteam.safebus.trip.application;

import java.util.List;

public interface CountingSourceQueryService {
    List<CountingSourceResult> listByBus(GetCountingSourcesByBusQuery query);
}
