package com.dreamteam.safebus.safetycase.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EmergencyCreationRetryTest {

    @Autowired CreateDriverEmergencyCommandService orchestrator;
    @MockitoBean EmergencyWriter writer;

    @Test
    void orchestrator_retries_onDataIntegrityViolation_secondAttemptSucceeds() {
        CreateDriverEmergencyResult expected = new CreateDriverEmergencyResult(
            UUID.randomUUID().toString(), "ACTIVE", "CRITICAL",
            Instant.parse("2030-01-01T10:00:00Z"), Instant.parse("2030-01-01T10:00:01Z"), false);

        when(writer.write(any()))
            .thenThrow(new DataIntegrityViolationException("simulated unique constraint"))
            .thenReturn(expected);

        CreateDriverEmergencyCommand cmd = new CreateDriverEmergencyCommand(
            expected.id(), 1L, Instant.now(), null, null);

        CreateDriverEmergencyResult actual = orchestrator.create(cmd);

        assertEquals(expected, actual);
        verify(writer, times(2)).write(any());
    }
}
