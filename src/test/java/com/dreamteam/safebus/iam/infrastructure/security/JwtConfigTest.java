package com.dreamteam.safebus.iam.infrastructure.security;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtConfigTest {

    @Test
    void validateSecret_secretShorterThan32Bytes_throwsIllegalStateException() {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "jwtSecret", "tooshort");

        IllegalStateException ex = assertThrows(IllegalStateException.class, config::validateSecret);
        assertTrue(ex.getMessage().contains("safebus.jwt.secret must be at least 32 bytes"));
    }

    @Test
    void validateSecret_secretExactly32Bytes_doesNotThrow() {
        JwtConfig config = new JwtConfig();
        ReflectionTestUtils.setField(config, "jwtSecret", "exactly-32-bytes-secret-here!!!!");

        config.validateSecret();
    }
}
