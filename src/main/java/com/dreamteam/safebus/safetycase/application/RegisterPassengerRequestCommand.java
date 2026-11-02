package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record RegisterPassengerRequestCommand(String id, Long journeyId, String message,
                                              Instant activatedAt, byte[] photo) {
}
