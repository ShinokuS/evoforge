package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Four tiny procedural water frames shared by every visible water cell. */
public final class ProceduralWaterArt {

    public static final int TILE_PIXELS = 16;
    public static final int FRAME_COUNT = 4;

    private static final int PADDING = 1;
    private static final int STRIDE = TILE_PIXELS + PADDING * 2;

    private final Texture texture;
    private final TextureRegion[] frames =
            new TextureRegion[FRAME_COUNT];

    public ProceduralWaterArt() {
        Pixmap atlas = new Pixmap(
                FRAME_COUNT * STRIDE,
                STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int ox = frame * STRIDE + PADDING;
            int oy = PADDING;
            drawWaterFrame(atlas, ox, oy, frame);
            bleedPadding(atlas, ox, oy);
        }

        texture = new Texture(atlas);
        texture.setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        atlas.dispose();

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            frames[frame] = new TextureRegion(
                    texture,
                    frame * STRIDE + PADDING,
                    PADDING,
                    TILE_PIXELS,
                    TILE_PIXELS);
        }
    }

    public TextureRegion frame(int frame) {
        return frames[Math.floorMod(frame, FRAME_COUNT)];
    }

    public void setFilter(Texture.TextureFilter filter) {
        if (filter == null) {
            throw new IllegalArgumentException("filter must not be null");
        }
        texture.setFilter(filter, filter);
    }

    public void dispose() {
        texture.dispose();
    }

    private static void drawWaterFrame(
            Pixmap pixmap,
            int ox,
            int oy,
            int frame) {

        pixmap.setColor(EvoForgePalette.WATER_BASE);
        pixmap.fillRectangle(
                ox,
                oy,
                TILE_PIXELS,
                TILE_PIXELS);

        // Static darker pixels keep the surface textured without flickering.
        pixmap.setColor(EvoForgePalette.WATER_DARK);
        for (int y = 1; y < TILE_PIXELS - 1; y++) {
            for (int x = 1; x < TILE_PIXELS - 1; x++) {
                if (Math.floorMod(x * 13 + y * 19, 43) == 0) {
                    pixmap.drawPixel(ox + x, oy + y);
                }
            }
        }

        // Horizontal highlights shift together between the four frames. All
        // cells share these frames; coordinate phase in WaterRenderer makes
        // neighbouring tiles read as one moving surface without per-cell state.
        pixmap.setColor(EvoForgePalette.WATER_LIGHT);
        wrappedSegment(pixmap, ox, oy, frame * 2 + 1, 4, 5);
        wrappedSegment(pixmap, ox, oy, frame * 2 + 9, 10, 4);
        wrappedSegment(pixmap, ox, oy, frame * 2 + 5, 14, 3);

        pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
        wrappedSegment(pixmap, ox, oy, frame * 2 + 3, 5, 2);
        wrappedSegment(pixmap, ox, oy, frame * 2 + 11, 11, 2);
    }

    private static void wrappedSegment(
            Pixmap pixmap,
            int ox,
            int oy,
            int startX,
            int y,
            int length) {

        for (int offset = 0; offset < length; offset++) {
            int x = Math.floorMod(startX + offset, TILE_PIXELS);
            pixmap.drawPixel(ox + x, oy + y);
        }
    }

    private static void bleedPadding(
            Pixmap pixmap,
            int ox,
            int oy) {

        for (int x = 0; x < TILE_PIXELS; x++) {
            pixmap.drawPixel(
                    ox + x,
                    oy - 1,
                    pixmap.getPixel(ox + x, oy));
            pixmap.drawPixel(
                    ox + x,
                    oy + TILE_PIXELS,
                    pixmap.getPixel(ox + x, oy + TILE_PIXELS - 1));
        }
        for (int y = -1; y <= TILE_PIXELS; y++) {
            int sourceY = Math.max(
                    oy,
                    Math.min(oy + TILE_PIXELS - 1, oy + y));
            pixmap.drawPixel(
                    ox - 1,
                    oy + y,
                    pixmap.getPixel(ox, sourceY));
            pixmap.drawPixel(
                    ox + TILE_PIXELS,
                    oy + y,
                    pixmap.getPixel(ox + TILE_PIXELS - 1, sourceY));
        }
    }
}
