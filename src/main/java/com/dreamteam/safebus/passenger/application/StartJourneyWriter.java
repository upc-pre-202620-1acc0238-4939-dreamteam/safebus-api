package com.dreamteam.safebus.passenger.application;

public interface StartJourneyWriter {
    StartJourneyResult write(StartJourneyCommand command);
}
