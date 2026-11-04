package com.dreamteam.safebus.trip.domain.port;

public interface CountingSourceKeys {

    GeneratedKey generate();

    String hash(String rawKey);

    record GeneratedKey(String rawKey, String hash) {
        @Override
        public String toString() {
            return "GeneratedKey[rawKey=<redacted>, hash=<redacted>]";
        }
    }
}
