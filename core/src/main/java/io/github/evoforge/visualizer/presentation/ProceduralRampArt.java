package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.visualizer.visual.EvoForgePalette;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;

/** Procedural full-cell ramp art with boundary-free surface presentation. */
final class ProceduralRampArt {
    private static final int TILE = ProceduralLandscapePack.TILE_PIXELS;
    private static final int DIRECTIONS = 4;
    private static final int VARIANTS = ProceduralLandscapePack.SURFACE_VARIANTS;
    private static final int PADDING = 1;
    private static final int STRIDE = TILE + PADDING * 2;
    private static final int COLUMNS = 16;
    private static final int TILE_COUNT = DIRECTIONS * VARIANTS;
    private static final int ROWS = (TILE_COUNT + COLUMNS - 1) / COLUMNS;

    private final Texture texture;
    private final TextureRegion[][] regions = new TextureRegion[DIRECTIONS][VARIANTS];

    ProceduralRampArt() {
        Pixmap atlas = new Pixmap(
                COLUMNS * STRIDE,
                ROWS * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int variant = 0; variant < VARIANTS; variant++) {
                int index = index(direction, variant);
                int ox = (index % COLUMNS) * STRIDE + PADDING;
                int oy = (index / COLUMNS) * STRIDE + PADDING;
                draw(atlas, ox, oy, direction, variant);
                bleed(atlas, ox, oy);
            }
        }

        texture = new Texture(atlas);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        atlas.dispose();

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int variant = 0; variant < VARIANTS; variant++) {
                int index = index(direction, variant);
                regions[direction][variant] = new TextureRegion(
                        texture,
                        (index % COLUMNS) * STRIDE + PADDING,
                        (index / COLUMNS) * STRIDE + PADDING,
                        TILE,
                        TILE);
            }
        }
    }

    TextureRegion region(int riseX, int riseY, int topologyMask, int variant) {
        return regions[direction(riseX, riseY)][Math.floorMod(variant, VARIANTS)];
    }

    void dispose() {
        texture.dispose();
    }

    private static void draw(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int variant) {
        fill(pixmap, ox, oy, 0, 0, TILE, TILE, EvoForgePalette.GRASS_BASE);

        // A ramp is one continuous terrain surface, not a bordered object. Keep all four
        // outer pixels neutral. True discontinuities are owned by SurfaceReliefEdgeArt,
        // which already uses objective Shape boundary continuity to decide where a cliff exists.
        for (int y = 1; y < TILE - 1; y++) {
            for (int x = 1; x < TILE - 1; x++) {
                float highness = highness(direction, x, y);
                if (highness < 0.20f && ((x + y) & 1) == 0) {
                    pixel(pixmap, ox, oy, x, y, EvoForgePalette.GRASS_DARK);
                } else if (highness > 0.80f && ((x + y) & 1) == 0) {
                    pixel(pixmap, ox, oy, x, y, EvoForgePalette.GRASS_LIGHT);
                }
            }
        }

        addTexture(pixmap, ox, oy, direction, variant);
    }

    private static void addTexture(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int variant) {
        int state = 0x4F1BBCDC ^ direction * 193 ^ variant * 811;
        for (int i = 0; i < 6; i++) {
            state = mix(state + i * 73);
            int x = 2 + Math.floorMod(state, 12);
            state = mix(state ^ 0x27D4EB2D);
            int y = 2 + Math.floorMod(state, 12);
            pixel(
                    pixmap,
                    ox,
                    oy,
                    x,
                    y,
                    i % 3 == 0 ? EvoForgePalette.GRASS_LIGHT : EvoForgePalette.GRASS_DEEP);
        }
    }

    private static float highness(int direction, int x, int y) {
        return switch (direction) {
            case 0 -> (TILE - 1 - y) / (float) (TILE - 1);
            case 1 -> x / (float) (TILE - 1);
            case 2 -> y / (float) (TILE - 1);
            case 3 -> (TILE - 1 - x) / (float) (TILE - 1);
            default -> throw new IllegalArgumentException("unknown ramp direction " + direction);
        };
    }

    private static int direction(int riseX, int riseY) {
        if (riseX == 0 && riseY == 1) return 0;
        if (riseX == 1 && riseY == 0) return 1;
        if (riseX == 0 && riseY == -1) return 2;
        if (riseX == -1 && riseY == 0) return 3;
        throw new IllegalArgumentException(
                "unsupported ramp rise vector " + riseX + "," + riseY);
    }

    private static int index(int direction, int variant) {
        return direction * VARIANTS + variant;
    }

    private static void bleed(Pixmap p, int ox, int oy) {
        for (int i = 0; i < TILE; i++) {
            p.drawPixel(ox + i, oy - 1, p.getPixel(ox + i, oy));
            p.drawPixel(ox + i, oy + TILE, p.getPixel(ox + i, oy + TILE - 1));
            p.drawPixel(ox - 1, oy + i, p.getPixel(ox, oy + i));
            p.drawPixel(ox + TILE, oy + i, p.getPixel(ox + TILE - 1, oy + i));
        }
    }

    private static void fill(
            Pixmap p,
            int ox,
            int oy,
            int x,
            int y,
            int width,
            int height,
            int rgba) {
        p.setColor(rgba);
        p.fillRectangle(ox + x, oy + y, width, height);
    }

    private static void pixel(Pixmap p, int ox, int oy, int x, int y, int rgba) {
        p.drawPixel(ox + x, oy + y, rgba);
    }

    private static int mix(int value) {
        int mixed = value;
        mixed ^= mixed >>> 16;
        mixed *= 0x7FEB352D;
        mixed ^= mixed >>> 15;
        mixed *= 0x846CA68B;
        return mixed ^ (mixed >>> 16);
    }
}
