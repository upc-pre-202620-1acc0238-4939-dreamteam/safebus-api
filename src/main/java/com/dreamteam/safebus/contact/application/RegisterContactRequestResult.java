package com.dreamteam.safebus.contact.application;

import java.time.Instant;

public record RegisterContactRequestResult(
    String receiptReference,
    Instant receivedAt,
    boolean duplicate
) {}
