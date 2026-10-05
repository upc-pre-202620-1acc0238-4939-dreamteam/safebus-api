package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Driver;

public interface CreateDriverCommandService {
    Driver create(CreateDriverCommand command);
}
