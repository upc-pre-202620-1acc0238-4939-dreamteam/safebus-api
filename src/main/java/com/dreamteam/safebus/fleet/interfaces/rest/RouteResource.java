package com.dreamteam.safebus.fleet.interfaces.rest;

public record RouteResource(Long id, String name, String origin, String destination, boolean enabled) {}
