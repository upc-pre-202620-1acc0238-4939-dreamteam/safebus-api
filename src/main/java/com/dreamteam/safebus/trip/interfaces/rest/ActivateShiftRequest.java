package com.dreamteam.safebus.trip.interfaces.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ActivateShiftRequest(
    @NotNull Long assignmentId,
    @NotBlank String qrCredential
) {
    @Override
    public String toString() {
        return "ActivateShiftRequest[assignmentId=" + assignmentId + ", qrCredential=***]";
    }
}
