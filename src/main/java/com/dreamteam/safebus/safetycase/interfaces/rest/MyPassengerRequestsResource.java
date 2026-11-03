package com.dreamteam.safebus.safetycase.interfaces.rest;

import java.util.List;

public record MyPassengerRequestsResource(List<MyPassengerRequestResource> items, String message) {}
