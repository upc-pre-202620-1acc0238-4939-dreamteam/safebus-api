package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.RegisterPassengerRequestResult;

public final class PassengerRequestResourceFromResultAssembler {

    private PassengerRequestResourceFromResultAssembler() {}

    public static PassengerRequestResource toResource(RegisterPassengerRequestResult result) {
        return new PassengerRequestResource(result.requestId(), result.status(), result.receivedAt(),
            result.groupStatus(), result.contributorCount());
    }
}
