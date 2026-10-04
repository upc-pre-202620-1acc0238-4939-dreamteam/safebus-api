package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Bus;

public interface CreateBusCommandService {
    Bus create(CreateBusCommand command);
}
