package com.dreamteam.safebus.contact.application;

public record RegisterContactRequestCommand(
    String submissionId,
    String companyName,
    String contactName,
    String email,
    boolean consent
) {}
