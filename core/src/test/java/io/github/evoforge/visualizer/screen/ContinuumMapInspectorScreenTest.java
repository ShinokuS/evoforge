package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

final class ContinuumMapInspectorScreenTest {

    @Test
    void textureRasterKeepsWorldRowsInSourceOrder() {
        byte[] luminance = {
                0, 0,
                (byte) 0xFF, (byte) 0xFF
        };
        ByteBuffer rgba = ByteBuffer.allocate(2 * 2 * 4);

        ContinuumMapInspectorScreen.writeTexturePixels(luminance, 2, rgba);

        assertEquals(6, Byte.toUnsignedInt(rgba.get(0)), "first source row must remain the first texture row");
        assertEquals(6, Byte.toUnsignedInt(rgba.get(4)));
        assertEquals(199, Byte.toUnsignedInt(rgba.get(8)), "second source row must remain the second texture row");
        assertEquals(199, Byte.toUnsignedInt(rgba.get(12)));
        assertEquals(255, Byte.toUnsignedInt(rgba.get(3)));
        assertEquals(255, Byte.toUnsignedInt(rgba.get(15)));
    }

    @Test
    void seedInputAcceptsReadableDecimalAndHexForms() {
        assertEquals(42L, ContinuumMapInspectorScreen.parseSeed("42"));
        assertEquals(-42L, ContinuumMapInspectorScreen.parseSeed(" -42 "));
        assertEquals(0x45A10F0E2026L, ContinuumMapInspectorScreen.parseSeed("0x45A1_0F0E_2026"));
        assertThrows(NumberFormatException.class, () -> ContinuumMapInspectorScreen.parseSeed("not-a-seed"));
    }
}
