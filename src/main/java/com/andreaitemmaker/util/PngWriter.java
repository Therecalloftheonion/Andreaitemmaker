package com.andreaitemmaker.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Minimal pure-Java PNG encoder (RGBA, 8-bit, non-interlaced).
 * Used to generate textures for the resource pack without any image library.
 */
public final class PngWriter {

    private PngWriter() {
    }

    /**
     * Encode a width x height image of ARGB pixels into a PNG byte array.
     *
     * @param width  image width
     * @param height image height
     * @param argb   pixels in row-major order, each an int with alpha in the top byte
     */
    public static byte[] write(int width, int height, int[] argb) {
        if (argb.length != width * height) {
            throw new IllegalArgumentException("argb length " + argb.length + " != " + width + "x" + height);
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
            out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
            writeChunk(out, "IHDR", ihdr(width, height));
            writeChunk(out, "IDAT", idat(width, height, argb));
            writeChunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("PNG encoding failed", e);
        }
    }

    private static byte[] ihdr(int width, int height) {
        ByteArrayOutputStream b = new ByteArrayOutputStream(13);
        writeInt(b, width);
        writeInt(b, height);
        b.write(8);   // bit depth
        b.write(6);   // color type: RGBA
        b.write(0);   // compression
        b.write(0);   // filter
        b.write(0);   // interlace
        return b.toByteArray();
    }

    private static byte[] idat(int width, int height, int[] argb) throws IOException {
        // Raw scanlines with filter byte 0 (None) per row.
        byte[] raw = new byte[height * (1 + width * 4)];
        int pos = 0;
        for (int y = 0; y < height; y++) {
            raw[pos++] = 0;
            for (int x = 0; x < width; x++) {
                int p = argb[y * width + x];
                raw[pos++] = (byte) ((p >>> 16) & 0xFF); // r
                raw[pos++] = (byte) ((p >>> 8) & 0xFF);  // g
                raw[pos++] = (byte) (p & 0xFF);          // b
                raw[pos++] = (byte) ((p >>> 24) & 0xFF); // a
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length / 2);
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            out.write(buf, 0, n);
        }
        deflater.end();
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        out.write(typeBytes);
        out.write(data);
        writeInt(out, (int) crc.getValue());
    }

    private static void writeInt(ByteArrayOutputStream b, int v) {
        b.write((v >>> 24) & 0xFF);
        b.write((v >>> 16) & 0xFF);
        b.write((v >>> 8) & 0xFF);
        b.write(v & 0xFF);
    }
}
