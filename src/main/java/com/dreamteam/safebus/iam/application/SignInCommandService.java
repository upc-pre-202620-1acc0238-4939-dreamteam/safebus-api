package com.dreamteam.safebus.iam.application;

public interface SignInCommandService {
    SignInResult signIn(SignInCommand command);
}
