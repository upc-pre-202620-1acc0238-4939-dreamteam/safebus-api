package com.dreamteam.safebus.shared.domain.exceptions;

public class RuleViolationException extends DomainException {

    public RuleViolationException(String message) {
        super(message);
    }

    @Override
    public String code() {
        return "RULE_VIOLATION";
    }
}
