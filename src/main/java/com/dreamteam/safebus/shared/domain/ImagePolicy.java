package com.dreamteam.safebus.shared.domain;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Iterator;

public final class ImagePolicy {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 25_000_000L;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private ImagePolicy() {}

    public static ImageInfo inspect(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE, "image is empty");
        }
        if (content.length > MAX_SIZE_BYTES) {
            throw new InvalidImageException(InvalidImageException.Reason.TOO_LARGE,
                "image exceeds 5 MiB limit");
        }

        String contentType = detectContentType(content);

        int width;
        int height;
        ImageReader reader = null;
        try {
            ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(content));
            if (iis == null) {
                throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE,
                    "cannot create image input stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE,
                    "no image reader available");
            }
            reader = readers.next();
            reader.setInput(iis, true, true);
            width  = reader.getWidth(0);
            height = reader.getHeight(0);
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE,
                "cannot read image header");
        } finally {
            if (reader != null) reader.dispose();
        }

        if ((long) width * height > MAX_PIXELS) {
            throw new InvalidImageException(InvalidImageException.Reason.TOO_MANY_PIXELS,
                "image pixel count exceeds 25 megapixels");
        }

        try {
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(content));
            if (decoded == null) {
                throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE,
                    "image could not be decoded");
            }
        } catch (InvalidImageException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidImageException(InvalidImageException.Reason.UNDECODABLE,
                "image decoding failed");
        }

        return new ImageInfo(contentType, width, height);
    }

    private static String detectContentType(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(content, PNG_MAGIC))  return "image/png";
        throw new InvalidImageException(InvalidImageException.Reason.UNSUPPORTED_TYPE,
            "image type not supported; must be JPEG or PNG");
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) return false;
        }
        return true;
    }
}
