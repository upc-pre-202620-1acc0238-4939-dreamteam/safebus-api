package com.dreamteam.safebus.shared.infrastructure;

import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.model.StoredImage;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

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

    @Override
    @Transactional(readOnly = true)
    public Optional<LoadedImage> load(Long imageId) {
        if (imageId == null) {
            return Optional.empty();
        }
        return repository.findById(imageId).map(i -> new LoadedImage(i.getContentType(), i.getData()));
    }
}
