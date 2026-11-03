package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.DeclineGroupResult;

public final class DeclinedGroupResourceFromResultAssembler {

    private DeclinedGroupResourceFromResultAssembler() {}

    public static DeclinedGroupResource toResource(DeclineGroupResult result) {
        return new DeclinedGroupResource(result.groupId(), result.status(), result.decidedAt(),
            result.reason(), result.response());
    }
}
