package com.dreamteam.safebus.trip.application;

public interface OccupancyCommandService {
    InitializeOccupancyResult initialize(InitializeOccupancyCommand command);
    RecordHeartbeatResult heartbeat(RecordHeartbeatCommand command);
}
