package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Bus;

public class BusResourceFromEntityAssembler {

    private BusResourceFromEntityAssembler() {}

    public static BusResource toResource(Bus bus) {
        return new BusResource(bus.getId(), bus.getPlate(), bus.getQrCode(), bus.isEnabled());
    }
}
