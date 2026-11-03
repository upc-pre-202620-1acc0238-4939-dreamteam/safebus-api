package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.GetMyPassengerRequestsResult;
import com.dreamteam.safebus.safetycase.application.MyPassengerRequestResult;

public final class MyPassengerRequestResourceFromResultAssembler {

    private MyPassengerRequestResourceFromResultAssembler() {}

    public static MyPassengerRequestResource toResource(MyPassengerRequestResult result) {
        MyPassengerRequestResult.Decision decision = result.decision();
        return new MyPassengerRequestResource(result.requestId(), result.message(), result.evidenceReference(),
            result.submittedAt(), result.groupReference(), result.contributorCount(), result.status().name(),
            decision == null ? null
                : new MyPassengerRequestResource.Decision(decision.result(), decision.decidedAt(),
                    decision.response()));
    }

    public static MyPassengerRequestsResource toResource(GetMyPassengerRequestsResult result) {
        return new MyPassengerRequestsResource(
            result.items().stream().map(MyPassengerRequestResourceFromResultAssembler::toResource).toList(),
            result.message());
    }
}
