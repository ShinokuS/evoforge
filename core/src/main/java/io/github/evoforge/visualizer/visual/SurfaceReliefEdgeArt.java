package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Tiny transparent terrain-edge atlas used by the Surface renderer.
 *
 * <p>Relief is communicated by the brown terrain edge itself rather than an
 * always-on line overlay: a tile above its neighbour gets a lighter earth edge,
 * while a tile below its neighbour gets a darker earth edge.</p>
 */
public final class SurfaceReliefEdgeArt {

    public enum Side { NORTH, EAST, SOUTH, WEST }

    private static final Side[] SIDES_BY_INDEX = Side.values();
    private static final int TILE = ProceduralLandscapePack.TILE_PIXELS;
    private static final int TONES = 2;
    private static final int SIDES = SIDES_BY_INDEX.length;

    private final Texture texture;
    private final TextureRegion[][] regions = new TextureRegion[TONES][SIDES];

    public SurfaceReliefEdgeArt() {
        Pixmap atlas = new Pixmap(TILE * SIDES, TILE * TONES, Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        for (int tone = 0; tone < TONES; tone++) {
            boolean raised = tone == 0;
            for (int side = 0; side < SIDES; side++) {
                drawEdge(
                        atlas,
                        side * TILE,
                        tone * TILE,
                        SIDES_BY_INDEX[side],
                        raised);
            }
        }

        texture = new Texture(atlas);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        atlas.dispose();

        for (int tone = 0; tone < TONES; tone++) {
            for (int side = 0; side < SIDES; side++) {
                regions[tone][side] = new TextureRegion(
                        texture,
                        side * TILE,
                        tone * TILE,
                        TILE,
                        TILE);
            }
        }
    }

    public TextureRegion region(Side side, boolean raised) {
        if (side == null) throw new IllegalArgumentException("side must not be null");
        return regions[raised ? 0 : 1][side.ordinal()];
    }

    public void setFilter(Texture.TextureFilter filter) {
        if (filter == null) throw new IllegalArgumentException("filter must not be null");
        texture.setFilter(filter, filter);
    }

    public void dispose() {
        texture.dispose();
    }

    private static void drawEdge(Pixmap pixmap, int ox, int oy, Side side, boolean raised) {
        int outer = raised ? EvoForgePalette.EARTH_BASE : EvoForgePalette.EARTH_SHADOW;
        int inner = raised ? EvoForgePalette.EARTH_LIGHT : EvoForgePalette.EARTH_DARK;

        switch (side) {
            case NORTH -> {
                fill(pixmap, ox, oy, 0, 0, TILE, 1, outer);
                fill(pixmap, ox, oy, 0, 1, TILE, 1, inner);
            }
            case EAST -> {
                fill(pixmap, ox, oy, TILE - 2, 0, 1, TILE, inner);
                fill(pixmap, ox, oy, TILE - 1, 0, 1, TILE, outer);
            }
            case SOUTH -> {
                fill(pixmap, ox, oy, 0, TILE - 3, TILE, 2, inner);
                fill(pixmap, ox, oy, 0, TILE - 1, TILE, 1, outer);
            }
            case WEST -> {
                fill(pixmap, ox, oy, 0, 0, 1, TILE, outer);
                fill(pixmap, ox, oy, 1, 0, 1, TILE, inner);
            }
        }
    }

    private static void fill(
            Pixmap pixmap,
            int ox,
            int oy,
            int x,
            int y,
            int width,
            int height,
            int rgba) {
        pixmap.setColor(rgba);
        pixmap.fillRectangle(ox + x, oy + y, width, height);
    }
}
