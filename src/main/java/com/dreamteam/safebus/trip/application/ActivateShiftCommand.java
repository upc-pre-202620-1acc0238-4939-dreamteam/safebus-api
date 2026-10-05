package com.dreamteam.safebus.trip.application;

public record ActivateShiftCommand(Long assignmentId, String qrCredential) {}
