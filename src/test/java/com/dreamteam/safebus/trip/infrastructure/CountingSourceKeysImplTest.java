package com.dreamteam.safebus.trip.infrastructure;

import com.dreamteam.safebus.trip.domain.port.CountingSourceKeys.GeneratedKey;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CountingSourceKeysImplTest {

    private final CountingSourceKeysImpl keys = new CountingSourceKeysImpl();

    @Test
    void generatedKeyHasThePrefixAndFortyThreeRandomCharacters() {
        GeneratedKey key = keys.generate();

        assertTrue(key.rawKey().startsWith("sbk_"));
        assertEquals(4 + 43, key.rawKey().length());
        assertTrue(key.rawKey().matches("sbk_[A-Za-z0-9_-]{43}"));
    }

    @Test
    void hashIsTheSha256HexOfTheKeyAndNeverTheKeyItself() {
        GeneratedKey key = keys.generate();

        assertEquals(64, key.hash().length());
        assertEquals(key.hash(), keys.hash(key.rawKey()));
        assertNotEquals(key.rawKey(), key.hash());
        assertFalse(key.hash().contains(key.rawKey()));
    }

    @Test
    void knownVectorMatchesSha256() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", keys.hash("abc"));
    }

    @Test
    void generatedKeysAreDistinct() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            assertTrue(seen.add(keys.generate().rawKey()));
        }
    }

    @Test
    void toStringOfAGeneratedKeyRedactsTheSecret() {
        GeneratedKey key = keys.generate();

        assertFalse(key.toString().contains(key.rawKey()));
        assertFalse(key.toString().contains(key.hash()));
    }
}
