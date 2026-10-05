package com.dreamteam.safebus.fleet.application;

public record UpdateBusCapacityCommand(
    Long busId,
    Number capacity,
    String technicalRecordReference
) {}
