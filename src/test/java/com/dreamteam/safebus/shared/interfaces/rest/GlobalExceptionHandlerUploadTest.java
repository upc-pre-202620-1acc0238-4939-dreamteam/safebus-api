package com.dreamteam.safebus.shared.interfaces.rest;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerUploadTest {

    @Test
    void handleMaxUploadSize_returns422WithUploadTooLargeCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MaxUploadSizeExceededException ex = new MaxUploadSizeExceededException(6L * 1024 * 1024);

        ProblemDetail pd = handler.handleMaxUploadSize(ex);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY.value(), pd.getStatus());
        assertEquals("UPLOAD_TOO_LARGE", pd.getProperties().get("code"));
    }
}
