package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Route;

public interface CreateRouteCommandService {
    Route create(CreateRouteCommand command);
}
