package com.dreamteam.safebus.fleet.interfaces.rest;

public record UpdateBusCapacityRequest(
    Number capacity,
    String technicalRecordReference
) {}
