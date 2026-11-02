package com.dreamteam.safebus.safetycase.application;

public interface PassengerRequestWriter {
    RegisterPassengerRequestResult write(RegisterPassengerRequestCommand command);
}
