package com.dreamteam.safebus.safetycase.support;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;

public final class TestImages {

    private TestImages() {}

    public static byte[] jpeg(int seed) {
        return encode("jpg", seed);
    }

    public static byte[] png(int seed) {
        return encode("png", seed);
    }

    public static byte[] gif() {
        return encode("gif", 1);
    }

    public static byte[] truncatedJpeg() {
        byte[] full = jpeg(2);
        return Arrays.copyOf(full, full.length / 2);
    }

    // Trailing bytes after the end of a JPEG or PNG stream do not stop it from decoding.
    public static byte[] padded(byte[] image, int targetSize) {
        return Arrays.copyOf(image, targetSize);
    }

    private static byte[] encode(String format, int seed) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                img.setRGB(x, y, new Color((seed * 37 + x * 9) % 256, (seed * 91 + y * 13) % 256,
                    (seed * 53) % 256).getRGB());
            }
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(img, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
