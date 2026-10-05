package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CreateRouteCommandServiceImpl implements CreateRouteCommandService {

    private final RouteRepository routeRepository;
    private final CurrentUserProvider currentUserProvider;

    public CreateRouteCommandServiceImpl(RouteRepository routeRepository,
                                          CurrentUserProvider currentUserProvider) {
        this.routeRepository = routeRepository;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public Route create(CreateRouteCommand command) {
        var user = currentUserProvider.current();
        Route route = Route.create(user.companyId(), command.name(), command.origin(), command.destination());
        return routeRepository.save(route);
    }
}
