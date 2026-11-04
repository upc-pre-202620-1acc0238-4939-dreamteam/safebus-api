package com.dreamteam.safebus.shared.interfaces.rest;

import com.dreamteam.safebus.shared.domain.exceptions.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTooManyRequestsTest {

    @Test
    void handleTooManyRequests_returns429WithTheCode() {
        ProblemDetail pd = new GlobalExceptionHandler()
            .handleTooManyRequests(new TooManyRequestsException("LIMIT", "too many"));

        assertEquals(HttpStatus.TOO_MANY_REQUESTS.value(), pd.getStatus());
        assertEquals("LIMIT", pd.getProperties().get("code"));
        assertEquals("too many", pd.getDetail());
    }
}
