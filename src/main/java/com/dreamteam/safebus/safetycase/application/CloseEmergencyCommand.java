package com.dreamteam.safebus.safetycase.application;

public record CloseEmergencyCommand(
    String id,
    String outcome,
    String userResponse
) {}
