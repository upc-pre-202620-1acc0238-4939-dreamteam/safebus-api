package com.dreamteam.safebus.fleet.application;

public record CreateDriverCommand(String fullName, String loginId, String initialPassword) {}
