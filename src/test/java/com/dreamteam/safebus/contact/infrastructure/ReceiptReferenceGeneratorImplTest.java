package com.dreamteam.safebus.contact.infrastructure;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class ReceiptReferenceGeneratorImplTest {

    @Test
    void generate_usesTenUppercaseHexCharactersFromANewRandomUuid() {
        UUID first = UUID.fromString("abcdef12-3456-4789-8abc-123456789abc");
        UUID second = UUID.fromString("9876abcd-ef12-4789-8abc-123456789abc");
        ReceiptReferenceGeneratorImpl generator = new ReceiptReferenceGeneratorImpl();

        try (MockedStatic<UUID> uuids = mockStatic(UUID.class)) {
            uuids.when(UUID::randomUUID).thenReturn(first, second);
            assertEquals("CR-ABCDEF1234", generator.generate());
            assertEquals("CR-9876ABCDEF", generator.generate());
            uuids.verify(UUID::randomUUID, times(2));
        }
    }

    @Test
    void generate_realUuidProducesExpectedFormat() {
        assertTrue(new ReceiptReferenceGeneratorImpl().generate().matches("CR-[0-9A-F]{10}"));
    }
}
