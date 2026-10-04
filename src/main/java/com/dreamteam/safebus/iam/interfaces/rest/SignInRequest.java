package com.dreamteam.safebus.iam.interfaces.rest;

import jakarta.validation.constraints.NotBlank;

public record SignInRequest(@NotBlank String loginId, @NotBlank String password) {

    @Override
    public String toString() {
        return "SignInRequest[loginId=" + loginId + "]";
    }
}
