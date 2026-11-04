package com.dreamteam.safebus.trip.application;

import com.dreamteam.safebus.trip.domain.model.EventOutcome;
import com.dreamteam.safebus.trip.domain.model.OccupancyEventType;

// GAP and NEGATIVE are committed marks; the caller turns them into errors once the transaction is over
public record OccupancyEventWriteResult(EventOutcome outcome, boolean duplicate, String eventId,
                                        long sequence, OccupancyEventType type, int count,
                                        long expectedSequence) {}
