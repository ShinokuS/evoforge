package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Procedural art for solid material intersected by a horizontal Z cut.
 *
 * <p>Cut material is intentionally dark and neutral. It communicates occluding
 * mass/depth rather than pretending to be another walkable terrain material.
 * Ramp appearance stays owned by {@link ProceduralLandscapePack}; the same Ramp
 * art is reused at every slice and only environmental shading changes.</p>
 */
public final class ProceduralSliceArt {

    private static final int TILE_PIXELS = ProceduralLandscapePack.TILE_PIXELS;
    private static final int VARIANTS = ProceduralLandscapePack.SURFACE_VARIANTS;
    private static final int MASK_COUNT = 256;
    private static final int TOTAL_COUNT = VARIANTS * MASK_COUNT;

    private static final int PADDING = 1;
    private static final int STRIDE = TILE_PIXELS + PADDING * 2;
    private static final int COLUMNS = 32;
    private static final int ROWS = (TOTAL_COUNT + COLUMNS - 1) / COLUMNS;

    private final Texture texture;
    private final TextureRegion[][] solids =
            new TextureRegion[VARIANTS][MASK_COUNT];

    public ProceduralSliceArt() {
        Pixmap atlas = new Pixmap(
                COLUMNS * STRIDE,
                ROWS * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        generateSolids(atlas);

        texture = new Texture(atlas);
        texture.setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        atlas.dispose();

        indexRegions();
    }

    public TextureRegion solid(
            int topologyMask,
            int variant) {

        return solids[Math.floorMod(variant, VARIANTS)]
                [LandscapeTopology.normalize(topologyMask)];
    }

    public void dispose() {
        texture.dispose();
    }

    private static void generateSolids(
            Pixmap atlas) {

        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int mask = 0; mask < MASK_COUNT; mask++) {
                int index = variant * MASK_COUNT + mask;
                Origin origin = origin(index);
                drawSolid(
                        atlas,
                        origin.x(),
                        origin.y(),
                        LandscapeTopology.normalize(mask),
                        variant);
                bleedPadding(atlas, origin.x(), origin.y());
            }
        }
    }

    private void indexRegions() {
        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int mask = 0; mask < MASK_COUNT; mask++) {
                solids[variant][mask] = region(variant * MASK_COUNT + mask);
            }
        }
    }

    private TextureRegion region(
            int index) {

        Origin origin = origin(index);
        return new TextureRegion(
                texture,
                origin.x(),
                origin.y(),
                TILE_PIXELS,
                TILE_PIXELS);
    }

    private static Origin origin(
            int index) {

        return new Origin(
                (index % COLUMNS) * STRIDE + PADDING,
                (index / COLUMNS) * STRIDE + PADDING);
    }

    private static void drawSolid(
            Pixmap pixmap,
            int ox,
            int oy,
            int mask,
            int variant) {

        fill(
                pixmap,
                ox,
                oy,
                0,
                0,
                TILE_PIXELS,
                TILE_PIXELS,
                EvoForgePalette.CUT_BASE);
        scatterCutDetail(pixmap, ox, oy, variant * 101 + mask * 17);
        drawStrata(pixmap, ox, oy, variant);

        if (!LandscapeTopology.contains(mask, LandscapeTopology.N)) {
            fill(pixmap, ox, oy, 0, 0, 16, 1, EvoForgePalette.CUT_EDGE);
            fill(pixmap, ox, oy, 1, 1, 14, 1, EvoForgePalette.CUT_LIGHT);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.W)) {
            fill(pixmap, ox, oy, 0, 0, 1, 16, EvoForgePalette.CUT_EDGE);
            fill(pixmap, ox, oy, 1, 1, 1, 14, EvoForgePalette.CUT_LIGHT);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.S)) {
            fill(pixmap, ox, oy, 0, 15, 16, 1, EvoForgePalette.CUT_DEEP);
            fill(pixmap, ox, oy, 1, 14, 14, 1, EvoForgePalette.CUT_DARK);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.E)) {
            fill(pixmap, ox, oy, 15, 0, 1, 16, EvoForgePalette.CUT_DEEP);
            fill(pixmap, ox, oy, 14, 1, 1, 14, EvoForgePalette.CUT_DARK);
        }

        drawInnerCutCorners(pixmap, ox, oy, mask);
    }

    private static void drawInnerCutCorners(
            Pixmap pixmap,
            int ox,
            int oy,
            int mask) {

        boolean n = LandscapeTopology.contains(mask, LandscapeTopology.N);
        boolean e = LandscapeTopology.contains(mask, LandscapeTopology.E);
        boolean s = LandscapeTopology.contains(mask, LandscapeTopology.S);
        boolean w = LandscapeTopology.contains(mask, LandscapeTopology.W);

        if (n && e && !LandscapeTopology.contains(mask, LandscapeTopology.NE)) {
            cutCorner(pixmap, ox, oy, 15, 0, -1, 1);
        }
        if (s && e && !LandscapeTopology.contains(mask, LandscapeTopology.SE)) {
            cutCorner(pixmap, ox, oy, 15, 15, -1, -1);
        }
        if (s && w && !LandscapeTopology.contains(mask, LandscapeTopology.SW)) {
            cutCorner(pixmap, ox, oy, 0, 15, 1, -1);
        }
        if (n && w && !LandscapeTopology.contains(mask, LandscapeTopology.NW)) {
            cutCorner(pixmap, ox, oy, 0, 0, 1, 1);
        }
    }

    private static void cutCorner(
            Pixmap pixmap,
            int ox,
            int oy,
            int x,
            int y,
            int towardX,
            int towardY) {

        pixel(pixmap, ox, oy, x, y, EvoForgePalette.CUT_EDGE);
        pixel(
                pixmap,
                ox,
                oy,
                x + towardX,
                y,
                EvoForgePalette.CUT_DARK);
        pixel(
                pixmap,
                ox,
                oy,
                x,
                y + towardY,
                EvoForgePalette.CUT_DARK);
    }

    private static void drawStrata(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        int yA = 4 + variant % 3;
        int yB = 10 + (variant + 1) % 2;

        for (int x = 2; x <= 6; x++) {
            pixel(pixmap, ox, oy, x, yA, EvoForgePalette.CUT_DARK);
        }
        for (int x = 9; x <= 13; x++) {
            pixel(pixmap, ox, oy, x, yB, EvoForgePalette.CUT_LIGHT);
        }
    }

    private static void scatterCutDetail(
            Pixmap pixmap,
            int ox,
            int oy,
            int seed) {

        int state = seed ^ 0x51ED270B;
        for (int i = 0; i < 10; i++) {
            state = mix(state + i * 59);
            int x = 2 + Math.floorMod(state, 12);
            state = mix(state ^ 0x9E3779B9);
            int y = 2 + Math.floorMod(state, 12);
            pixel(
                    pixmap,
                    ox,
                    oy,
                    x,
                    y,
                    i % 4 == 0
                            ? EvoForgePalette.CUT_LIGHT
                            : EvoForgePalette.CUT_DARK);
        }
    }

    private static void bleedPadding(
            Pixmap pixmap,
            int ox,
            int oy) {

        for (int i = 0; i < TILE_PIXELS; i++) {
            pixmap.drawPixel(
                    ox + i,
                    oy - 1,
                    pixmap.getPixel(ox + i, oy));
            pixmap.drawPixel(
                    ox + i,
                    oy + TILE_PIXELS,
                    pixmap.getPixel(ox + i, oy + TILE_PIXELS - 1));
            pixmap.drawPixel(
                    ox - 1,
                    oy + i,
                    pixmap.getPixel(ox, oy + i));
            pixmap.drawPixel(
                    ox + TILE_PIXELS,
                    oy + i,
                    pixmap.getPixel(ox + TILE_PIXELS - 1, oy + i));
        }

        pixmap.drawPixel(
                ox - 1,
                oy - 1,
                pixmap.getPixel(ox, oy));
        pixmap.drawPixel(
                ox + TILE_PIXELS,
                oy - 1,
                pixmap.getPixel(ox + TILE_PIXELS - 1, oy));
        pixmap.drawPixel(
                ox - 1,
                oy + TILE_PIXELS,
                pixmap.getPixel(ox, oy + TILE_PIXELS - 1));
        pixmap.drawPixel(
                ox + TILE_PIXELS,
                oy + TILE_PIXELS,
                pixmap.getPixel(
                        ox + TILE_PIXELS - 1,
                        oy + TILE_PIXELS - 1));
    }

    private static void fill(
            Pixmap pixmap,
            int ox,
            int oy,
            int x,
            int y,
            int width,
            int height,
            int color) {

        pixmap.setColor(color);
        pixmap.fillRectangle(
                ox + x,
                oy + y,
                width,
                height);
    }

    private static void pixel(
            Pixmap pixmap,
            int ox,
            int oy,
            int x,
            int y,
            int color) {

        if (x < 0 || y < 0 || x >= TILE_PIXELS || y >= TILE_PIXELS) {
            return;
        }
        pixmap.drawPixel(ox + x, oy + y, color);
    }

    private static int mix(
            int value) {

        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

    private record Origin(int x, int y) {
    }
}
