package com.dreamteam.safebus.trip.application;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
public class CountingSourceCommandServiceImpl implements CountingSourceCommandService {

    private final CountingSourceWriter writer;

    public CountingSourceCommandServiceImpl(CountingSourceWriter writer) {
        this.writer = writer;
    }

    @Override
    public CountingSourceCredentialResult register(RegisterCountingSourceCommand command) {
        return retryOnce(() -> writer.register(command));
    }

    @Override
    public CountingSourceCredentialResult rotate(RotateCountingSourceCommand command) {
        return retryOnce(() -> writer.rotate(command));
    }

    @Override
    public CountingSourceResult revoke(RevokeCountingSourceCommand command) {
        return retryOnce(() -> writer.revoke(command));
    }

    private <T> T retryOnce(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            return action.get();  // retry once in a new transaction
        }
    }
}
