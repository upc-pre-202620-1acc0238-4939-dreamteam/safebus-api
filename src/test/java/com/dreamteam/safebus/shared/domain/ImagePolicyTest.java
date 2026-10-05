package com.dreamteam.safebus.shared.domain;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ImagePolicyTest {

    // --- happy path ---

    @Test
    void inspect_validJpeg_returnsJpegInfo() {
        byte[] jpeg = jpeg(10, 10);
        ImageInfo info = ImagePolicy.inspect(jpeg);
        assertEquals("image/jpeg", info.contentType());
        assertEquals(10, info.width());
        assertEquals(10, info.height());
    }

    @Test
    void inspect_validPng_returnsPngInfo() {
        byte[] png = png(10, 10);
        ImageInfo info = ImagePolicy.inspect(png);
        assertEquals("image/png", info.contentType());
        assertEquals(10, info.width());
        assertEquals(10, info.height());
    }

    // --- size boundary ---

    @Test
    void inspect_exactlyFiveMib_accepted() {
        byte[] padded = Arrays.copyOf(jpeg(5, 5), 5 * 1024 * 1024);
        assertDoesNotThrow(() -> ImagePolicy.inspect(padded));
    }

    @Test
    void inspect_fiveMibPlusOne_rejectsTooLarge() {
        byte[] padded = Arrays.copyOf(jpeg(5, 5), 5 * 1024 * 1024 + 1);
        InvalidImageException ex = assertThrows(InvalidImageException.class,
            () -> ImagePolicy.inspect(padded));
        assertEquals(InvalidImageException.Reason.TOO_LARGE, ex.getReason());
    }

    // --- unsupported types ---

    @Test
    void inspect_gifBytes_rejectsUnsupportedType() {
        byte[] gif = {'G', 'I', 'F', '8', '9', 'a', 0, 0, 0, 0, 0, 0, 0};
        assertThrows(InvalidImageException.class, () -> ImagePolicy.inspect(gif));
    }

    @Test
    void inspect_textBytes_rejectsUnsupportedType() {
        byte[] text = "Hello, world!".getBytes();
        assertThrows(InvalidImageException.class, () -> ImagePolicy.inspect(text));
    }

    @Test
    void inspect_emptyArray_rejectsUndecodable() {
        InvalidImageException ex = assertThrows(InvalidImageException.class,
            () -> ImagePolicy.inspect(new byte[0]));
        assertEquals(InvalidImageException.Reason.UNDECODABLE, ex.getReason());
    }

    // --- structural failures ---

    @Test
    void inspect_truncatedJpeg_rejectsUndecodable() {
        byte[] truncated = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}; // just the magic, nothing more
        InvalidImageException ex = assertThrows(InvalidImageException.class,
            () -> ImagePolicy.inspect(truncated));
        assertEquals(InvalidImageException.Reason.UNDECODABLE, ex.getReason());
    }

    @Test
    void inspect_corruptedPng_rejectsUndecodable() {
        // Valid PNG magic, then invalid/incomplete structure
        byte[] corrupted = new byte[20];
        byte[] magic = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        System.arraycopy(magic, 0, corrupted, 0, magic.length);
        // rest is zeros — not a valid PNG structure
        InvalidImageException ex = assertThrows(InvalidImageException.class,
            () -> ImagePolicy.inspect(corrupted));
        assertEquals(InvalidImageException.Reason.UNDECODABLE, ex.getReason());
    }

    // --- helpers ---

    static byte[] jpeg(int w, int h) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpeg", baos);
            return baos.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static byte[] png(int w, int h) {
        try {
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
