package com.dreamteam.safebus.fleet.interfaces.rest;

public record BusResource(Long id, String plate, String qrCode, boolean enabled) {}
