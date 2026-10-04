package com.dreamteam.safebus.iam.application;

public record SignInCommand(String loginId, String password) {
}
