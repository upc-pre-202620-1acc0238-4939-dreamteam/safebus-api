package com.dreamteam.safebus.shared.domain.exceptions;

public class ConflictException extends DomainException {

    public ConflictException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "CONFLICT";
    }
}
