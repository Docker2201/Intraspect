package com.sergey.pisarev.ai;

import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Запись картинки JavaFX в PNG своими руками.
 *
 * <p>Обычно для этого берут {@code SwingFXUtils} и {@code ImageIO}, но это тянет в
 * сборку целый модуль {@code javafx.swing}. Ради одной картинки для нейросети это
 * дорого, а формат PNG простой: заголовок, сжатые строки, конец. Здесь всё на
 * {@link Deflater} и {@link CRC32} из java.base.
 */
public final class PngWriter {
    private static final byte[] SIGNATURE = {(byte) 137, 80, 78, 71, 13, 10, 26, 10};

    private PngWriter() {
    }

    /** PNG с прозрачностью (RGBA, 8 бит на канал). */
    public static byte[] encode(Image image) {
        if (image == null) {
            return new byte[0];
        }
        int width = (int) Math.round(image.getWidth());
        int height = (int) Math.round(image.getHeight());
        if (width <= 0 || height <= 0) {
            return new byte[0];
        }
        PixelReader pixels = image.getPixelReader();
        if (pixels == null) {
            return new byte[0];
        }
        // Строка PNG: байт фильтра (0 - без фильтра) и дальше RGBA каждой точки.
        byte[] raw = new byte[height * (1 + width * 4)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0;
            for (int x = 0; x < width; x++) {
                int argb = pixels.getArgb(x, y);
                raw[at++] = (byte) ((argb >> 16) & 0xFF);
                raw[at++] = (byte) ((argb >> 8) & 0xFF);
                raw[at++] = (byte) (argb & 0xFF);
                raw[at++] = (byte) ((argb >>> 24) & 0xFF);
            }
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 3 + 1024);
            out.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream(13);
            writeInt(header, width);
            writeInt(header, height);
            header.write(8);
            header.write(6);
            header.write(0);
            header.write(0);
            header.write(0);
            chunk(out, "IHDR", header.toByteArray());
            chunk(out, "IDAT", deflate(raw));
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException impossible) {
            // ByteArrayOutputStream не бросает, но подпись метода этого требует.
            return new byte[0];
        }
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(data);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length / 4 + 256);
            byte[] buffer = new byte[64 * 1024];
            while (!deflater.finished()) {
                int done = deflater.deflate(buffer);
                out.write(buffer, 0, done);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] name = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.write(name);
        out.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}
