package com.dreamteam.safebus.safetycase.application;

import java.util.List;

public record GetMyPassengerRequestsResult(List<MyPassengerRequestResult> items, String message) {}
