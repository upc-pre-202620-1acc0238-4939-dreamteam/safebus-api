package com.dreamteam.safebus.shared.domain.exceptions;

public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }

    public abstract String code();
}
