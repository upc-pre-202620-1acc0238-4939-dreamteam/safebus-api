package com.dreamteam.safebus.shared.infrastructure;

import com.dreamteam.safebus.shared.application.ImageStoragePort;
import com.dreamteam.safebus.shared.domain.repository.StoredImageRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class ImageStoragePortImplTest {

    @Autowired ImageStoragePort imageStoragePort;
    @Autowired StoredImageRepository storedImageRepository;
    @Autowired TransactionTemplate transactionTemplate;

    @AfterEach
    void tearDown() {
        storedImageRepository.deleteAll();
    }

    @Test
    void store_withoutTransaction_throwsIllegalTransactionState() {
        byte[] content = minimalJpeg();
        assertThrows(IllegalTransactionStateException.class,
            () -> imageStoragePort.store(content, "image/jpeg"));
    }

    @Test
    @Transactional
    void store_withinTransaction_savesAndReturnsPositiveId() {
        byte[] content = minimalJpeg();
        Long id = imageStoragePort.store(content, "image/jpeg");
        assertNotNull(id);
        assertTrue(id > 0);
    }

    @Test
    void load_returnsTheStoredContentTypeAndBytes() {
        byte[] content = minimalJpeg();
        Long id = storeInTransaction(content);

        ImageStoragePort.LoadedImage loaded = imageStoragePort.load(id).orElseThrow();

        assertEquals("image/jpeg", loaded.contentType());
        assertArrayEquals(content, loaded.content());
    }

    @Test
    void load_ofAnUnknownOrNullIdIsEmpty() {
        assertTrue(imageStoragePort.load(Long.MAX_VALUE).isEmpty());
        assertTrue(imageStoragePort.load(null).isEmpty());
    }

    @Test
    void load_doesNotRequireAnOpenTransaction() {
        Long id = storeInTransaction(minimalJpeg());

        assertDoesNotThrow(() -> imageStoragePort.load(id));
    }

    private Long storeInTransaction(byte[] content) {
        return transactionTemplate.execute(status -> imageStoragePort.store(content, "image/jpeg"));
    }

    static byte[] minimalJpeg() {
        try {
            BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
