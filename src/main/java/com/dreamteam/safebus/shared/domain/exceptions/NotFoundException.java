package com.dreamteam.safebus.shared.domain.exceptions;

public class NotFoundException extends DomainException {

    public NotFoundException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "NOT_FOUND";
    }
}
