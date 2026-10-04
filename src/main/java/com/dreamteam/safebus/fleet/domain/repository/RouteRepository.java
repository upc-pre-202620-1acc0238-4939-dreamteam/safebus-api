package com.dreamteam.safebus.fleet.domain.repository;

import com.dreamteam.safebus.fleet.domain.model.Route;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, Long> {
}
