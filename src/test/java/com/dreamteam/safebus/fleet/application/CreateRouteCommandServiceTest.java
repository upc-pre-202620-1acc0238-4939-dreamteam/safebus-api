package com.dreamteam.safebus.fleet.application;

import com.dreamteam.safebus.fleet.domain.model.Route;
import com.dreamteam.safebus.fleet.domain.repository.RouteRepository;
import com.dreamteam.safebus.shared.application.AuthenticatedUser;
import com.dreamteam.safebus.shared.application.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateRouteCommandServiceTest {

    @Mock RouteRepository routeRepository;
    @Mock CurrentUserProvider currentUserProvider;

    CreateRouteCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CreateRouteCommandServiceImpl(routeRepository, currentUserProvider);
        when(currentUserProvider.current()).thenReturn(new AuthenticatedUser(1L, "SUPERVISOR", 1L));
    }

    @Test
    void create_validRoute_savedWithCompanyId() {
        Route saved = Route.create(1L, "Route A", "Origin", "Destination");
        when(routeRepository.save(any(Route.class))).thenReturn(saved);

        Route result = service.create(new CreateRouteCommand("Route A", "Origin", "Destination"));

        assertEquals("Route A", result.getName());
        assertEquals(1L, result.getCompanyId());
        verify(routeRepository).save(any(Route.class));
    }
}
