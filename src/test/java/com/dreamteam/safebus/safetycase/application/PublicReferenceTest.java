package com.dreamteam.safebus.safetycase.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class PublicReferenceTest {

    @ParameterizedTest
    @CsvSource({
        "11111111-1111-4111-8111-111111111111, EM-BD7662A5",
        "22222222-2222-4222-8222-222222222222, EM-B454F82C",
        "abcdef12-3456-4789-8abc-123456789abc, EM-810A0747"
    })
    void of_returnsDeterministicSha256PrefixInPublicFormatWithoutOriginalId(String id, String expected) {
        String first = PublicReference.of(id);
        String second = PublicReference.of(id);

        assertEquals(expected, first);
        assertEquals(first, second);
        assertTrue(first.matches("EM-[0-9A-F]{8}"));
        assertFalse(first.contains(id));
    }

    @Test
    void of_differentIdsProduceDifferentReferences() {
        assertNotEquals(PublicReference.of("11111111-1111-4111-8111-111111111111"),
            PublicReference.of("22222222-2222-4222-8222-222222222222"));
    }
}
