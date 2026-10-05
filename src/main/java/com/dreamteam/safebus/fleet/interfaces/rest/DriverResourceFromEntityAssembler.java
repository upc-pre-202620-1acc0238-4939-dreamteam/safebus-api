package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Driver;

public class DriverResourceFromEntityAssembler {

    private DriverResourceFromEntityAssembler() {}

    public static DriverResource toResource(Driver driver, String loginId) {
        return new DriverResource(
            driver.getId(),
            driver.getFullName(),
            loginId,
            driver.getQrCredential(),
            driver.getQrCredentialExpiresAt());
    }
}
