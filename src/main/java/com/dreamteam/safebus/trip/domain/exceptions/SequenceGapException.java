package com.dreamteam.safebus.trip.domain.exceptions;

import com.dreamteam.safebus.shared.domain.exceptions.ConflictException;

public class SequenceGapException extends ConflictException {

    private final long expectedSequence;

    public SequenceGapException(long expectedSequence) {
        super("SEQUENCE_GAP", "events must arrive in order; the next expected sequence is " + expectedSequence);
        this.expectedSequence = expectedSequence;
    }

    public long expectedSequence() {
        return expectedSequence;
    }
}
