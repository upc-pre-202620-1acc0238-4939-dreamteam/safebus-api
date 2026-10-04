package com.dreamteam.safebus.fleet.interfaces.rest;

import com.dreamteam.safebus.fleet.domain.model.Route;

public class RouteResourceFromEntityAssembler {

    private RouteResourceFromEntityAssembler() {}

    public static RouteResource toResource(Route route) {
        return new RouteResource(
            route.getId(), route.getName(), route.getOrigin(),
            route.getDestination(), route.isEnabled());
    }
}
