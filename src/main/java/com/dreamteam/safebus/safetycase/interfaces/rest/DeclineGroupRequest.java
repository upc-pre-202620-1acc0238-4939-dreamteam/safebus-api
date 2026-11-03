package com.dreamteam.safebus.safetycase.interfaces.rest;

public record DeclineGroupRequest(
    String reason,
    String response
) {}
