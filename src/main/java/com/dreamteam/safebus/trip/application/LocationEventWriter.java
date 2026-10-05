package com.dreamteam.safebus.trip.application;

public interface LocationEventWriter {
    RecordLocationEventResult write(RecordLocationEventCommand command);
}
