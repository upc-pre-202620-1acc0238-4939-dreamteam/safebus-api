package com.dreamteam.safebus.trip.application;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class LocationEventRetryTest {

    @Autowired RecordLocationEventCommandService orchestrator;
    @MockitoBean LocationEventWriter writer;

    @Test
    void orchestrator_retries_onDataIntegrityViolation_secondAttemptSucceeds() {
        RecordLocationEventResult expected = new RecordLocationEventResult(
            "evt-retry-id", 1L, 10L, Instant.parse("2030-01-01T10:00:00Z"), true, false);

        when(writer.write(any()))
            .thenThrow(new DataIntegrityViolationException("simulated unique constraint"))
            .thenReturn(expected);

        RecordLocationEventCommand cmd = new RecordLocationEventCommand(
            "evt-retry-id", 1L, null, Instant.now(), 5.0, -12.0, -77.0);

        RecordLocationEventResult actual = orchestrator.record(cmd);

        assertEquals(expected, actual);
        verify(writer, times(2)).write(any());
    }
}
