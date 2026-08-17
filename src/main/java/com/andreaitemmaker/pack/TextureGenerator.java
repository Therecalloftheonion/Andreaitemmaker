package com.andreaitemmaker.pack;

import com.andreaitemmaker.util.PngWriter;

/**
 * Procedurally generates simple textures (solid, gradient, diagonal stripes, checkerboard)
 * so admins can create good-looking content without providing any image files.
 */
public final class TextureGenerator {

    public enum Pattern {
        SOLID, GRADIENT, DIAGONAL, CHECKER
    }

    private TextureGenerator() {
    }

    /** Parse a "#RRGGBB" or "#RRGGBBAA" hex string into an ARGB int. Throws on bad input. */
    public static int parseColor(String hex) {
        if (hex == null) {
            throw new IllegalArgumentException("color is null");
        }
        String h = hex.trim();
        if (h.startsWith("#")) {
            h = h.substring(1);
        }
        if (h.length() != 6 && h.length() != 8) {
            throw new IllegalArgumentException("invalid color '" + hex + "' (expected #RRGGBB or #RRGGBBAA)");
        }
        try {
            long v = Long.parseLong(h, 16);
            if (h.length() == 6) {
                return 0xFF000000 | (int) v;
            }
            // #RRGGBBAA
            int rgb = (int) (v >>> 8);
            int a = (int) (v & 0xFF);
            return (a << 24) | rgb;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid color '" + hex + "'", e);
        }
    }

    /**
     * Generate a size x size texture.
     *
     * @param size    edge length in pixels (16, 32 or 64)
     * @param pattern fill pattern
     * @param color1  primary ARGB color
     * @param color2  secondary ARGB color (unused for SOLID)
     * @param outline add a dark 1px border
     */
    public static int[] generate(int size, Pattern pattern, int color1, int color2, boolean outline) {
        int[] px = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                px[y * size + x] = pixel(size, x, y, pattern, color1, color2);
            }
        }
        if (outline) {
            darkenBorder(px, size);
        }
        return px;
    }

    /** Generate a 64x32 armor layer texture (upper body layer or leggings layer). */
    public static int[] armorLayer(Pattern pattern, int color1, int color2, boolean outline) {
        int w = 64;
        int h = 32;
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = pixel(Math.max(w, h), x, y, pattern, color1, color2);
            }
        }
        if (outline) {
            for (int x = 0; x < w; x++) {
                px[x] = darken(px[x]);
                px[(h - 1) * w + x] = darken(px[x]);
            }
            for (int y = 0; y < h; y++) {
                px[y * w] = darken(px[y * w]);
                px[y * w + (w - 1)] = darken(px[y * w + (w - 1)]);
            }
        }
        return px;
    }

    public static byte[] toPng(int[] px, int size) {
        return PngWriter.write(size, size, px);
    }

    private static int pixel(int size, int x, int y, Pattern pattern, int c1, int c2) {
        return switch (pattern) {
            case SOLID -> c1;
            case GRADIENT -> lerp(c1, c2, (float) y / (size - 1));
            case DIAGONAL -> (((x + y) / (size / 4)) % 2 == 0) ? c1 : c2;
            case CHECKER -> {
                int cell = Math.max(1, size / 4);
                yield (((x / cell) + (y / cell)) % 2 == 0) ? c1 : c2;
            }
        };
    }

    private static int lerp(int a, int b, float t) {
        int ar = (a >>> 16) & 0xFF, ag = (a >>> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24) & 0xFF;
        int br = (b >>> 16) & 0xFF, bg = (b >>> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24) & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        int al = Math.round(aa + (ba - aa) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    private static int darken(int c) {
        int r = (int) (((c >>> 16) & 0xFF) * 0.6);
        int g = (int) (((c >>> 8) & 0xFF) * 0.6);
        int b = (int) ((c & 0xFF) * 0.6);
        return (c & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static void darkenBorder(int[] px, int size) {
        for (int x = 0; x < size; x++) {
            px[x] = darken(px[x]);
            px[(size - 1) * size + x] = darken(px[(size - 1) * size + x]);
        }
        for (int y = 0; y < size; y++) {
            px[y * size] = darken(px[y * size]);
            px[y * size + (size - 1)] = darken(px[y * size + (size - 1)]);
        }
    }
}
