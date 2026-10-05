package com.dreamteam.safebus.shared.infrastructure;

import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.model.StoredImage;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ImageStoragePortImpl implements ImageStoragePort {

    private final StoredImageRepository repository;
    private final Clock clock;

    public ImageStoragePortImpl(StoredImageRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    // MANDATORY: image storage must always run within the caller's transaction
    @Transactional(propagation = Propagation.MANDATORY)
    public Long store(byte[] content, String contentType) {
        return repository.saveAndFlush(StoredImage.create(content, contentType, clock)).getId();
    }
}
