package com.dreamteam.safebus.shared.domain.repository;

import com.dreamteam.safebus.shared.domain.model.StoredImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoredImageRepository extends JpaRepository<StoredImage, Long> {}
