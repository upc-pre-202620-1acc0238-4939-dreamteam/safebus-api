package com.dreamteam.safebus.shared.application;

public interface ImageStoragePort {
    Long store(byte[] content, String contentType);
}
