package com.dreamteam.safebus.safetycase.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class PublicReference {

    private PublicReference() {}

    public static String of(String emergencyId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(emergencyId.getBytes(StandardCharsets.UTF_8));
            return "EM-" + HexFormat.of().withUpperCase().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
