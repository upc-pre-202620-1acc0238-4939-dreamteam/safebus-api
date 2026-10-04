package com.dreamteam.safebus.fleet.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record CreateDriverRequest(
    @NotBlank String fullName,
    @NotBlank String loginId,
    @NotBlank String initialPassword
) {
    @Override
    public String toString() {
        return "CreateDriverRequest[fullName=" + fullName + ", loginId=" + loginId + ", initialPassword=***]";
    }
}
