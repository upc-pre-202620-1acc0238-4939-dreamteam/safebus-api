package com.dreamteam.safebus.iam.infrastructure.security;

import com.dreamteam.safebus.iam.domain.model.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component
public class JwtTokenIssuer {

    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration ttl;

    public JwtTokenIssuer(JwtEncoder jwtEncoder, Clock clock,
                          @Value("${safebus.jwt.ttl}") Duration ttl) {
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.ttl = ttl;
    }

    public String issue(Long userId, UserRole role, Long companyId) {
        Instant now = Instant.now(clock);
        JwtClaimsSet.Builder builder = JwtClaimsSet.builder()
                .issuer("safebus")
                .subject(String.valueOf(userId))
                .issuedAt(now)
                .expiresAt(now.plus(ttl))
                .claim("role", role.name());
        if (companyId != null) {
            builder.claim("companyId", companyId);
        }
        return jwtEncoder.encode(JwtEncoderParameters.from(builder.build())).getTokenValue();
    }

    public Duration getTtl() {
        return ttl;
    }
}
