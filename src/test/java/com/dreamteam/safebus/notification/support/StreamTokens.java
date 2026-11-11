package com.dreamteam.safebus.notification.support;

import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;

/** Mints signed tokens against the real system clock, as a real client would hold them. */
public final class StreamTokens {

    private final JwtEncoder encoder;

    public StreamTokens(JwtEncoder encoder) {
        this.encoder = encoder;
    }

    public String supervisor(long userId, long companyId, Duration validFor) {
        return mint("SUPERVISOR", userId, companyId, validFor);
    }

    public String mint(String role, long userId, Long companyId, Duration validFor) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder().issuer("safebus")
            .subject(String.valueOf(userId))
            .issuedAt(now.plus(validFor).minus(Duration.ofHours(1)))
            .expiresAt(now.plus(validFor))
            .claim("role", role);
        if (companyId != null) {
            claims.claim("companyId", companyId);
        }
        return encoder.encode(JwtEncoderParameters.from(claims.build())).getTokenValue();
    }
}
