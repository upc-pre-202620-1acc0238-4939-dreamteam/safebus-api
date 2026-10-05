package com.dreamteam.safebus.shared.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Entity
public class StoredImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String contentType;

    @Column(nullable = false)
    private int sizeBytes;

    @Lob
    @Column(nullable = false)
    private byte[] data;

    @Column(nullable = false)
    private Instant createdAt;

    protected StoredImage() {}

    public static StoredImage create(byte[] content, String contentType, Clock clock) {
        StoredImage img = new StoredImage();
        img.contentType = contentType;
        img.sizeBytes = content.length;
        img.data = content.clone();
        img.createdAt = Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
        return img;
    }

    public Long getId() { return id; }
    public String getContentType() { return contentType; }
    public int getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
