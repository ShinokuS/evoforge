package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/** Small procedural water atlas shared by every visible water cell. */
public final class ProceduralWaterArt {

    public static final int TILE_PIXELS = 16;
    public static final int FRAME_COUNT = 6;

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

        // A darker counter-moving ripple makes frame changes visible even at
        // moderate zoom without creating a second render pass.
        pixmap.setColor(EvoForgePalette.WATER_DARK);
        wrappedSegment(pixmap, ox, oy, 13 - frame * 2, 3, 5);
        wrappedSegment(pixmap, ox, oy, 6 - frame * 2, 9, 4);
        wrappedSegment(pixmap, ox, oy, 1 - frame * 2, 13, 3);

        // Broad highlights move together for every tile. Because the pattern
        // wraps inside the tile, repeated cells still read as one animated sheet.
        int shift = frame * 3;
        pixmap.setColor(EvoForgePalette.WATER_LIGHT);
        wrappedSegment(pixmap, ox, oy, shift + 1, 5, 6);
        wrappedSegment(pixmap, ox, oy, shift + 9, 8, 5);
        wrappedSegment(pixmap, ox, oy, shift + 4, 12, 4);

        pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
        wrappedSegment(pixmap, ox, oy, shift + 3, 5, 3);
        wrappedSegment(pixmap, ox, oy, shift + 12, 8, 2);
        wrappedSegment(pixmap, ox, oy, shift + 7, 12, 2);
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
