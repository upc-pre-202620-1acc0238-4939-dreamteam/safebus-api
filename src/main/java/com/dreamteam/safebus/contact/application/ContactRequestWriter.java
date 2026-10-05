package com.dreamteam.safebus.contact.application;

public interface ContactRequestWriter {

    RegisterContactRequestResult write(RegisterContactRequestCommand command);
}
