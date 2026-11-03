package com.dreamteam.safebus.safetycase.application;

import java.time.Instant;

public record ApproveGroupResult(Long groupId, String emergencyId, String status, String priority,
                                 Instant approvedAt, boolean replay) {
}
