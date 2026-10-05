package com.dreamteam.safebus.passenger.application;

public record RegisterPassengerCommand(
        String loginId,
        String rawPassword,
        String dni,
        String termsVersion,
        byte[] facePhotoBytes
) {}
