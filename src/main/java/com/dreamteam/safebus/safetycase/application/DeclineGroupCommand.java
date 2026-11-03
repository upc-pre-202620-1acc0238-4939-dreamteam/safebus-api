package com.dreamteam.safebus.safetycase.application;

public record DeclineGroupCommand(Long groupId, String reason, String response) {
}
