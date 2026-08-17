package com.andreaitemmaker;

import com.andreaitemmaker.pack.TextureGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextureGeneratorTest {

    @Test
    void parsesHexColors() {
        assertEquals(0xFF4F7CFF, TextureGenerator.parseColor("#4f7cff"));
        assertEquals(0x80FF0000, TextureGenerator.parseColor("#FF000080"));
        assertThrows(IllegalArgumentException.class, () -> TextureGenerator.parseColor("red"));
        assertThrows(IllegalArgumentException.class, () -> TextureGenerator.parseColor("#12345"));
    }

    @Test
    void solidPatternIsUniform() {
        int[] px = TextureGenerator.generate(16, TextureGenerator.Pattern.SOLID, 0xFF112233, 0, false);
        for (int p : px) {
            assertEquals(0xFF112233, p);
        }
    }

    @Test
    void checkerUsesBothColors() {
        int[] px = TextureGenerator.generate(16, TextureGenerator.Pattern.CHECKER, 0xFFFFFFFF, 0xFF000000, false);
        int cell00 = px[0];          // (0,0) -> color1
        int cell10 = px[4];          // (4,0) -> color2
        int cell01 = px[4 * 16];     // (0,4) -> color2
        int cell11 = px[4 * 16 + 4]; // (4,4) -> color1
        assertNotEquals(cell00, cell10);
        assertEquals(cell00, cell11);
        assertEquals(cell10, cell01);
    }

    @Test
    void outlineDarkensBorder() {
        int[] px = TextureGenerator.generate(16, TextureGenerator.Pattern.SOLID, 0xFFFFFFFF, 0, true);
        int border = px[0];
        int center = px[8 * 16 + 8];
        assertEquals(0xFFFFFFFF, center);
        assertNotEquals(0xFFFFFFFF, border);
    }

    @Test
    void armorLayerIs64x32() {
        int[] px = TextureGenerator.armorLayer(TextureGenerator.Pattern.GRADIENT, 0xFF4F7CFF, 0xFF1B2A6B, true);
        assertEquals(64 * 32, px.length);
    }
}
