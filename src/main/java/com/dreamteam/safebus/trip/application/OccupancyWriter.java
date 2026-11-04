package com.dreamteam.safebus.trip.application;

public interface OccupancyWriter {
    InitializeOccupancyResult initialize(InitializeOccupancyCommand command);
    RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command);
}
