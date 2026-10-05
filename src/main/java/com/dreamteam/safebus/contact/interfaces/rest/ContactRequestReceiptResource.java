package com.dreamteam.safebus.contact.interfaces.rest;

import java.time.Instant;

public record ContactRequestReceiptResource(
    String receiptReference,
    Instant receivedAt
) {}
