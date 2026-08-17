package com.andreaitemmaker;

import com.andreaitemmaker.util.PngWriter;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PngWriterTest {

    @Test
    void writesDecodablePng() throws Exception {
        int size = 16;
        int[] px = new int[size * size];
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF4F7CFF; // opaque blue
        }
        px[0] = 0x80FF0000; // semi-transparent red at top-left
        byte[] png = PngWriter.write(size, size, px);

        BufferedImage img = ImageIO.read(new ByteArrayInputStream(png));
        assertEquals(size, img.getWidth());
        assertEquals(size, img.getHeight());
        assertEquals(0x80FF0000, img.getRGB(0, 0)); // semi-transparent red preserved
        assertEquals(0xFF4F7CFF, img.getRGB(8, 8));
    }

    @Test
    void rejectsWrongPixelCount() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> PngWriter.write(16, 16, new int[10]));
    }
}
