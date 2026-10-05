package com.dreamteam.safebus.safetycase.application;

public interface EmergencyWriter {
    CreateDriverEmergencyResult write(CreateDriverEmergencyCommand command);
}
