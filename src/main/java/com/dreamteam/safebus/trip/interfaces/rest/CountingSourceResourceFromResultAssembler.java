package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.CountingSourceResult;

public final class CountingSourceResourceFromResultAssembler {

    private CountingSourceResourceFromResultAssembler() {}

    public static CountingSourceResource toResource(CountingSourceResult r) {
        return new CountingSourceResource(r.sourceId(), r.busId(), r.label(), r.status(),
            r.createdAt(), r.rotatedAt(), r.revokedAt());
    }
}
