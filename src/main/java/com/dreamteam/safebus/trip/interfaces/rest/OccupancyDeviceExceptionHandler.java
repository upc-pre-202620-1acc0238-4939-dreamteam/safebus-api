package com.dreamteam.safebus.trip.interfaces.rest;

import com.dreamteam.safebus.trip.domain.exceptions.SequenceGapException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Scoped to the device routes so the rest of the API keeps its current behavior
@RestControllerAdvice(assignableTypes = OccupancyDeviceController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OccupancyDeviceExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
        pd.setProperty("code", "VALIDATION_FAILED");
        return pd;
    }

    @ExceptionHandler(SequenceGapException.class)
    public ProblemDetail handleGap(SequenceGapException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setProperty("code", ex.code());
        pd.setProperty("expectedSequence", ex.expectedSequence());
        return pd;
    }
}
