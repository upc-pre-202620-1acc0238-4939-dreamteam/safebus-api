package com.dreamteam.safebus.trip.application;

public interface CountingSourceWriter {
    CountingSourceCredentialResult register(RegisterCountingSourceCommand command);
    CountingSourceCredentialResult rotate(RotateCountingSourceCommand command);
    CountingSourceResult revoke(RevokeCountingSourceCommand command);
}
