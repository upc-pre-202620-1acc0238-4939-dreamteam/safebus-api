package com.dreamteam.safebus.safetycase.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

public final class PublicReference {

    private static final int PREFIX_LENGTH = 8;

    private PublicReference() {}

    public static String of(String emergencyId) {
        return "EM-" + hashPrefix(emergencyId);
    }

    // The one identifier of a group on the wire: the review queue reference and the PassengerReviewRequired event id.
    // Groups stored before the column existed get a deterministic UUID derived from their database id.
    public static String groupRecordId(Long groupId, String storedReference) {
        if (storedReference != null && !storedReference.isBlank()) {
            return storedReference;
        }
        return UUID.nameUUIDFromBytes(("request-group:" + groupId).getBytes(StandardCharsets.UTF_8)).toString();
    }

    // Display prefix only: nobody looks a group up by it. Groups stored before the column existed fall back to a hash.
    public static String ofGroup(Long groupId, String storedReference) {
        if (storedReference != null && storedReference.length() >= PREFIX_LENGTH) {
            return "GR-" + storedReference.substring(0, PREFIX_LENGTH).toUpperCase(Locale.ROOT);
        }
        return "GR-" + hashPrefix("request-group:" + groupId);
    }

    private static String hashPrefix(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(hash, 0, 4);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
