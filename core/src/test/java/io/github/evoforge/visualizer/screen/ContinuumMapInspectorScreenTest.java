package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorScreenTest {

    @Test
    void textureRasterKeepsWorldRowsInSourceOrder() {
        byte[] mapCodes = {
                0, 0,
                (byte) 0xFF, (byte) 0xFF
        };
        ByteBuffer rgba = ByteBuffer.allocate(2 * 2 * 4);

        ContinuumMapInspectorScreen.writeTexturePixels(mapCodes, 2, rgba);

        for (int channel = 0; channel < 4; channel++) {
            assertEquals(rgba.get(channel), rgba.get(4 + channel));
            assertEquals(rgba.get(8 + channel), rgba.get(12 + channel));
        }
        assertNotEquals(rgba.get(0), rgba.get(8), "different source rows must retain different palette entries");
        assertEquals(255, Byte.toUnsignedInt(rgba.get(3)));
        assertEquals(255, Byte.toUnsignedInt(rgba.get(15)));
    }

    @Test
    void hillshadeChangesBrightnessWithoutChangingLowlandGreenHueFamily() {
        int lowlandBand = 3;
        byte dark = (byte) (0x80 | (lowlandBand << 3));
        byte light = (byte) (0x80 | (lowlandBand << 3) | 0x07);
        ByteBuffer rgba = ByteBuffer.allocate(16);

        ContinuumMapInspectorScreen.writeTexturePixels(
                new byte[] {dark, light, dark, light},
                2,
                rgba);

        int darkR = Byte.toUnsignedInt(rgba.get(0));
        int darkG = Byte.toUnsignedInt(rgba.get(1));
        int lightR = Byte.toUnsignedInt(rgba.get(4));
        int lightG = Byte.toUnsignedInt(rgba.get(5));
        assertTrue(darkG > darkR, "shaded lowland must remain green rather than becoming brown");
        assertTrue(lightG > lightR, "lit lowland must remain in the same green hue family");
        assertTrue(lightR > darkR && lightG > darkG, "shade bits should only brighten/darken the base hue");
    }

    @Test
    void seedInputAcceptsReadableDecimalAndHexForms() {
        assertEquals(42L, ContinuumMapInspectorScreen.parseSeed("42"));
        assertEquals(-42L, ContinuumMapInspectorScreen.parseSeed(" -42 "));
        assertEquals(0x45A10F0E2026L, ContinuumMapInspectorScreen.parseSeed("0x45A1_0F0E_2026"));
        assertThrows(NumberFormatException.class, () -> ContinuumMapInspectorScreen.parseSeed("not-a-seed"));
    }
}
