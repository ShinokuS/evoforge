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
    private final TextureRegion[][] frames =
            new TextureRegion[WaterMotion.values().length][FRAME_COUNT];

    public ProceduralWaterArt() {
        WaterMotion[] motions = WaterMotion.values();
        Pixmap atlas = new Pixmap(
                FRAME_COUNT * STRIDE,
                motions.length * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        for (WaterMotion motion : motions) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                int ox = frame * STRIDE + PADDING;
                int oy = motion.ordinal() * STRIDE + PADDING;
                drawWaterFrame(atlas, ox, oy, motion, frame);
                bleedPadding(atlas, ox, oy);
            }
        }

        texture = new Texture(atlas);
        texture.setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        atlas.dispose();

        for (WaterMotion motion : motions) {
            for (int frame = 0; frame < FRAME_COUNT; frame++) {
                frames[motion.ordinal()][frame] = new TextureRegion(
                        texture,
                        frame * STRIDE + PADDING,
                        motion.ordinal() * STRIDE + PADDING,
                        TILE_PIXELS,
                        TILE_PIXELS);
            }
        }
    }

    /** Compatibility: unqualified Water animation is calm Water. */
    public TextureRegion frame(int frame) {
        return frame(WaterMotion.CALM, frame);
    }

    public TextureRegion frame(WaterMotion motion, int frame) {
        if (motion == null) throw new IllegalArgumentException("motion must not be null");
        return frames[motion.ordinal()][Math.floorMod(frame, FRAME_COUNT)];
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
            WaterMotion motion,
            int frame) {

        pixmap.setColor(EvoForgePalette.WATER_BASE);
        pixmap.fillRectangle(ox, oy, TILE_PIXELS, TILE_PIXELS);

        if (motion == WaterMotion.CALM) {
            drawCalm(pixmap, ox, oy, frame);
        } else if (motion == WaterMotion.FALLING) {
            drawFalling(pixmap, ox, oy, frame);
        } else {
            drawDirectionalFlow(pixmap, ox, oy, motion, frame);
        }
    }

    /**
     * Still-water shimmer. Every frame uses the same fixed anchors; only their
     * brightness footprint breathes. The whole calm lake therefore shares one
     * coherent pulse instead of acquiring travelling per-cell phase.
     */
    private static void drawCalm(Pixmap pixmap, int ox, int oy, int frame) {
        int pulse = calmPulseLevel(frame);

        pixmap.setColor(EvoForgePalette.WATER_DARK);
        wrappedSegment(pixmap, ox, oy, 1, 4, 5);
        wrappedSegment(pixmap, ox, oy, 9, 11, 4);
        wrappedSegment(pixmap, ox, oy, 4, 14, 3);

        pixmap.setColor(EvoForgePalette.WATER_LIGHT);
        wrappedSegment(pixmap, ox, oy, 4, 7, 5);
        wrappedSegment(pixmap, ox, oy, 11, 13, 3);
        wrappedSegment(pixmap, ox, oy, 0, 1, 3);

        if (pulse >= 1) {
            pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
            wrappedSegment(pixmap, ox, oy, 6, 7, 3);
            wrappedSegment(pixmap, ox, oy, 12, 13, 2);
        }
        if (pulse >= 2) {
            pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
            wrappedSegment(pixmap, ox, oy, 2, 4, 3);
            ringPixel(pixmap, ox, oy, 7, 8);
            ringPixel(pixmap, ox, oy, 13, 12);
        }
        if (pulse >= 3) {
            pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
            ringPixel(pixmap, ox, oy, 7, 6);
            ringPixel(pixmap, ox, oy, 8, 6);
            ringPixel(pixmap, ox, oy, 7, 7);
            ringPixel(pixmap, ox, oy, 8, 7);
            wrappedSegment(pixmap, ox, oy, 11, 11, 3);
        }
    }

    /** Symmetric six-frame pulse; no frame translates the calm-water anchors. */
    static int calmPulseLevel(int frame) {
        return switch (Math.floorMod(frame, FRAME_COUNT)) {
            case 0 -> 0;
            case 1, 5 -> 1;
            case 2, 4 -> 2;
            default -> 3;
        };
    }

    /** Repeating moving dashes make local downhill direction legible without arrows. */
    private static void drawDirectionalFlow(
            Pixmap pixmap,
            int ox,
            int oy,
            WaterMotion motion,
            int frame) {

        int shift = frame * 3;
        pixmap.setColor(EvoForgePalette.WATER_DARK);
        directionalSegment(pixmap, ox, oy, motion, shift - 5, 3, 5);
        directionalSegment(pixmap, ox, oy, motion, shift + 3, 9, 4);
        directionalSegment(pixmap, ox, oy, motion, shift + 10, 13, 4);

        pixmap.setColor(EvoForgePalette.WATER_LIGHT);
        directionalSegment(pixmap, ox, oy, motion, shift + 1, 4, 6);
        directionalSegment(pixmap, ox, oy, motion, shift + 8, 8, 5);
        directionalSegment(pixmap, ox, oy, motion, shift + 13, 12, 4);

        pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
        directionalSegment(pixmap, ox, oy, motion, shift + 5, 4, 2);
        directionalSegment(pixmap, ox, oy, motion, shift + 12, 8, 2);
    }

    /** Top-down churn for Water that is physically dropping through the cell below. */
    private static void drawFalling(Pixmap pixmap, int ox, int oy, int frame) {
        int phase = frame % 3;
        pixmap.setColor(EvoForgePalette.WATER_DARK);
        ringPixel(pixmap, ox, oy, 4 + phase, 4);
        ringPixel(pixmap, ox, oy, 11 - phase, 5);
        ringPixel(pixmap, ox, oy, 5, 11 - phase);
        ringPixel(pixmap, ox, oy, 11, 11);

        pixmap.setColor(EvoForgePalette.WATER_LIGHT);
        ringPixel(pixmap, ox, oy, 7 + phase, 6);
        ringPixel(pixmap, ox, oy, 8 - phase, 10);
        ringPixel(pixmap, ox, oy, 5 + phase, 8);

        pixmap.setColor(EvoForgePalette.WATER_HIGHLIGHT);
        ringPixel(pixmap, ox, oy, 8, 8);
        ringPixel(pixmap, ox, oy, 9 - phase, 7 + phase);
    }

    private static void directionalSegment(
            Pixmap pixmap,
            int ox,
            int oy,
            WaterMotion motion,
            int startAlong,
            int across,
            int length) {

        for (int offset = 0; offset < length; offset++) {
            int along = Math.floorMod(startAlong + offset, TILE_PIXELS);
            int px;
            int py;
            switch (motion) {
                case EAST -> {
                    px = along;
                    py = across;
                }
                case WEST -> {
                    px = TILE_PIXELS - 1 - along;
                    py = across;
                }
                case NORTH -> {
                    px = across;
                    py = TILE_PIXELS - 1 - along;
                }
                case SOUTH -> {
                    px = across;
                    py = along;
                }
                default -> throw new IllegalArgumentException(
                        "directional segment requires cardinal flow motion: " + motion);
            }
            pixmap.drawPixel(ox + px, oy + py);
        }
    }

    private static void ringPixel(Pixmap pixmap, int ox, int oy, int x, int y) {
        pixmap.drawPixel(
                ox + Math.floorMod(x, TILE_PIXELS),
                oy + Math.floorMod(y, TILE_PIXELS));
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
