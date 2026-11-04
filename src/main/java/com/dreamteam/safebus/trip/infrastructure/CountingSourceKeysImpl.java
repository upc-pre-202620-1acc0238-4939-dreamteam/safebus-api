package com.dreamteam.safebus.trip.infrastructure;

import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

// The key is 256 random bits, so a plain SHA-256 digest is enough and lets the digest be indexed for lookup.
@Component
public class CountingSourceKeysImpl implements CountingSourceKeys {

    static final String PREFIX = "sbk_";

    private final SecureRandom random = new SecureRandom();

    @Override
    public GeneratedKey generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        return new GeneratedKey(raw, hash(raw));
    }

    @Override
    public String hash(String rawKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
