package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.application.CountingSourceCredentialResult;

public final class CountingSourceCredentialResourceFromResultAssembler {

    private CountingSourceCredentialResourceFromResultAssembler() {}

    public static CountingSourceCredentialResource toResource(CountingSourceCredentialResult r) {
        return new CountingSourceCredentialResource(r.sourceId(), r.busId(), r.label(), r.status(),
            r.createdAt(), r.rotatedAt(), r.rawKey());
    }
}
