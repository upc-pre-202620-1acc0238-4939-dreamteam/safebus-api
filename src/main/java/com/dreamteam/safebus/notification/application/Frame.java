package com.dreamteam.safebus.notification.application;

public record Frame(String name, String id, Object payload) {

    public static Frame comment(String text) {
        return new Frame(null, null, text);
    }

    public boolean isComment() {
        return name == null;
    }
}
