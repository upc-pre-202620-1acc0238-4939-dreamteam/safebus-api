package com.dreamteam.safebus.safetycase.interfaces.rest;

import com.dreamteam.safebus.safetycase.application.ApproveGroupResult;

public final class ApprovedGroupResourceFromResultAssembler {

    private ApprovedGroupResourceFromResultAssembler() {}

    public static ApprovedGroupResource toResource(ApproveGroupResult result) {
        return new ApprovedGroupResource(result.groupId(), result.emergencyId(), result.status(),
            result.priority(), result.approvedAt());
    }
}
