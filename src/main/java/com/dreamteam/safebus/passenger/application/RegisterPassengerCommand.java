package com.dreamteam.safebus.passenger.application;

public record RegisterPassengerCommand(
        String rawPassword,
        String dni,
        String termsAccepted,
        String termsVersion,
        byte[] facePhotoBytes
) {}
