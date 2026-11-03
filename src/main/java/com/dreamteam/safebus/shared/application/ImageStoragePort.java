package com.dreamteam.safebus.shared.application;

import java.util.Optional;

public interface ImageStoragePort {
    Long store(byte[] content, String contentType);

    Optional<LoadedImage> load(Long imageId);

    record LoadedImage(String contentType, byte[] content) {}
}
