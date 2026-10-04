package com.dreamteam.safebus.shared.domain.exceptions;

public class RuleViolationException extends DomainException {

    public RuleViolationException(String message) {
        super("RULE_VIOLATION", message);
    }

    public RuleViolationException(String code, String message) {
        super(code, message);
    }
}
