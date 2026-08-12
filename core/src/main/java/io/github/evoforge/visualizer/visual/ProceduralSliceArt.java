package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;

/**
 * Procedural art used only when a horizontal Z slice cuts through terrain.
 *
 * <p>Surface tiles answer "what can be stood on". These tiles answer the
 * different question "what solid material intersects the selected plane" and
 * provide a small derived descent marker for the upper landing of a Ramp.</p>
 */
public final class ProceduralSliceArt {

    private static final int TILE_PIXELS = ProceduralLandscapePack.TILE_PIXELS;
    private static final int VARIANTS = ProceduralLandscapePack.SURFACE_VARIANTS;
    private static final int MASK_COUNT = 256;
    private static final int DIRECTIONS = 4;

    private static final int SOLID_COUNT = VARIANTS * MASK_COUNT;
    private static final int RAMP_CUT_COUNT = VARIANTS * DIRECTIONS;
    private static final int MARKER_COUNT = DIRECTIONS;
    private static final int TOTAL_COUNT =
            SOLID_COUNT + RAMP_CUT_COUNT + MARKER_COUNT;

    private static final int PADDING = 1;
    private static final int STRIDE = TILE_PIXELS + PADDING * 2;
    private static final int COLUMNS = 32;
    private static final int ROWS = (TOTAL_COUNT + COLUMNS - 1) / COLUMNS;

    private final Texture texture;
    private final TextureRegion[][] solids =
            new TextureRegion[VARIANTS][MASK_COUNT];
    private final TextureRegion[][] rampCuts =
            new TextureRegion[DIRECTIONS][VARIANTS];
    private final TextureRegion[] descentMarkers =
            new TextureRegion[DIRECTIONS];

    public ProceduralSliceArt() {
        Pixmap atlas = new Pixmap(
                COLUMNS * STRIDE,
                ROWS * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        generateSolids(atlas);
        generateRampCuts(atlas);
        generateDescentMarkers(atlas);

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

    public TextureRegion rampCut(
            RampShape shape,
            int variant) {

        return rampCuts[direction(shape)][Math.floorMod(variant, VARIANTS)];
    }

    public TextureRegion descentMarker(
            RampShape shape) {

        return descentMarkers[direction(shape)];
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

    private static void generateRampCuts(
            Pixmap atlas) {

        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                int index = SOLID_COUNT + variant * DIRECTIONS + direction;
                Origin origin = origin(index);
                drawRampCut(
                        atlas,
                        origin.x(),
                        origin.y(),
                        direction,
                        variant);
                bleedPadding(atlas, origin.x(), origin.y());
            }
        }
    }

    private static void generateDescentMarkers(
            Pixmap atlas) {

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            int index = SOLID_COUNT + RAMP_CUT_COUNT + direction;
            Origin origin = origin(index);
            drawDescentMarker(
                    atlas,
                    origin.x(),
                    origin.y(),
                    direction);
            bleedPadding(atlas, origin.x(), origin.y());
        }
    }

    private void indexRegions() {
        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int mask = 0; mask < MASK_COUNT; mask++) {
                solids[variant][mask] = region(variant * MASK_COUNT + mask);
            }
        }

        for (int variant = 0; variant < VARIANTS; variant++) {
            for (int direction = 0; direction < DIRECTIONS; direction++) {
                rampCuts[direction][variant] = region(
                        SOLID_COUNT + variant * DIRECTIONS + direction);
            }
        }

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            descentMarkers[direction] = region(
                    SOLID_COUNT + RAMP_CUT_COUNT + direction);
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
                EvoForgePalette.EARTH_BASE);
        scatterRock(pixmap, ox, oy, variant * 101 + mask * 17);
        drawStrata(pixmap, ox, oy, variant);

        if (!LandscapeTopology.contains(mask, LandscapeTopology.N)) {
            fill(pixmap, ox, oy, 0, 0, 16, 1, EvoForgePalette.OUTLINE);
            fill(pixmap, ox, oy, 1, 1, 14, 1, EvoForgePalette.EARTH_LIGHT);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.W)) {
            fill(pixmap, ox, oy, 0, 0, 1, 16, EvoForgePalette.OUTLINE);
            fill(pixmap, ox, oy, 1, 1, 1, 14, EvoForgePalette.EARTH_LIGHT);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.S)) {
            fill(pixmap, ox, oy, 0, 15, 16, 1, EvoForgePalette.OUTLINE);
            fill(pixmap, ox, oy, 1, 14, 14, 1, EvoForgePalette.EARTH_SHADOW);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.E)) {
            fill(pixmap, ox, oy, 15, 0, 1, 16, EvoForgePalette.OUTLINE);
            fill(pixmap, ox, oy, 14, 1, 1, 14, EvoForgePalette.EARTH_SHADOW);
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

        pixel(pixmap, ox, oy, x, y, EvoForgePalette.OUTLINE);
        pixel(
                pixmap,
                ox,
                oy,
                x + towardX,
                y,
                EvoForgePalette.EARTH_SHADOW);
        pixel(
                pixmap,
                ox,
                oy,
                x,
                y + towardY,
                EvoForgePalette.EARTH_SHADOW);
    }

    private static void drawStrata(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        int yA = 4 + variant % 3;
        int yB = 10 + (variant + 1) % 2;

        for (int x = 2; x <= 6; x++) {
            pixel(pixmap, ox, oy, x, yA, EvoForgePalette.EARTH_DARK);
        }
        for (int x = 9; x <= 13; x++) {
            pixel(pixmap, ox, oy, x, yB, EvoForgePalette.EARTH_LIGHT);
        }
    }

    private static void scatterRock(
            Pixmap pixmap,
            int ox,
            int oy,
            int seed) {

        int state = seed ^ 0x51ED270B;
        for (int i = 0; i < 13; i++) {
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
                            ? EvoForgePalette.EARTH_LIGHT
                            : EvoForgePalette.EARTH_DARK);
        }
    }

    private static void drawRampCut(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int variant) {

        fill(
                pixmap,
                ox,
                oy,
                0,
                0,
                TILE_PIXELS,
                TILE_PIXELS,
                EvoForgePalette.EARTH_BASE);
        scatterRock(
                pixmap,
                ox,
                oy,
                0x324A9 + direction * 97 + variant * 211);

        for (int y = 0; y < TILE_PIXELS; y++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!cutChannel(direction, x, y)) {
                    continue;
                }
                pixel(
                        pixmap,
                        ox,
                        oy,
                        x,
                        y,
                        EvoForgePalette.EARTH_SHADOW);
            }
        }

        outlineChannel(pixmap, ox, oy, direction);
        drawCutBands(pixmap, ox, oy, direction);
    }

    private static boolean cutChannel(
            int direction,
            int x,
            int y) {

        if (x < 0 || y < 0 || x >= TILE_PIXELS || y >= TILE_PIXELS) {
            return false;
        }

        float highness = highness(direction, x, y);
        float cross = direction == 0 || direction == 2 ? x : y;
        int width = 4 + Math.round((1f - highness) * 7f);
        return Math.abs(cross - 7.5f) <= width * 0.5f;
    }

    private static void outlineChannel(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction) {

        for (int y = 0; y < TILE_PIXELS; y++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!cutChannel(direction, x, y)) {
                    continue;
                }

                boolean edge = !cutChannel(direction, x - 1, y)
                        || !cutChannel(direction, x + 1, y)
                        || !cutChannel(direction, x, y - 1)
                        || !cutChannel(direction, x, y + 1);
                if (edge) {
                    pixel(
                            pixmap,
                            ox,
                            oy,
                            x,
                            y,
                            x <= 7 || y <= 7
                                    ? EvoForgePalette.EARTH_LIGHT
                                    : EvoForgePalette.OUTLINE);
                }
            }
        }
    }

    private static void drawCutBands(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction) {

        for (int band : new int[] {5, 10}) {
            for (int cross = 4; cross <= 11; cross++) {
                int x;
                int y;
                if (direction == 0 || direction == 2) {
                    x = cross;
                    y = direction == 0 ? 15 - band : band;
                } else {
                    x = direction == 1 ? band : 15 - band;
                    y = cross;
                }

                if (cutChannel(direction, x, y)) {
                    pixel(
                            pixmap,
                            ox,
                            oy,
                            x,
                            y,
                            EvoForgePalette.EARTH_DARK);
                }
            }
        }
    }

    private static void drawDescentMarker(
            Pixmap pixmap,
            int ox,
            int oy,
            int riseDirection) {

        // Marker is placed on the upper landing and faces back toward the Ramp.
        switch (riseDirection) {
            case 0 -> markerSouth(pixmap, ox, oy); // Ramp rises north (+Y)
            case 1 -> markerWest(pixmap, ox, oy);  // Ramp rises east (+X)
            case 2 -> markerNorth(pixmap, ox, oy); // Ramp rises south (-Y)
            case 3 -> markerEast(pixmap, ox, oy);  // Ramp rises west (-X)
            default -> throw new IllegalArgumentException(
                    "unknown rise direction " + riseDirection);
        }
    }

    private static void markerSouth(
            Pixmap pixmap,
            int ox,
            int oy) {

        fill(pixmap, ox, oy, 5, 13, 6, 2, EvoForgePalette.OUTLINE);
        fill(pixmap, ox, oy, 6, 12, 4, 1, EvoForgePalette.EARTH_LIGHT);
        pixel(pixmap, ox, oy, 5, 12, EvoForgePalette.EARTH_DARK);
        pixel(pixmap, ox, oy, 10, 12, EvoForgePalette.EARTH_DARK);
    }

    private static void markerNorth(
            Pixmap pixmap,
            int ox,
            int oy) {

        fill(pixmap, ox, oy, 5, 1, 6, 2, EvoForgePalette.OUTLINE);
        fill(pixmap, ox, oy, 6, 3, 4, 1, EvoForgePalette.EARTH_LIGHT);
        pixel(pixmap, ox, oy, 5, 3, EvoForgePalette.EARTH_DARK);
        pixel(pixmap, ox, oy, 10, 3, EvoForgePalette.EARTH_DARK);
    }

    private static void markerWest(
            Pixmap pixmap,
            int ox,
            int oy) {

        fill(pixmap, ox, oy, 1, 5, 2, 6, EvoForgePalette.OUTLINE);
        fill(pixmap, ox, oy, 3, 6, 1, 4, EvoForgePalette.EARTH_LIGHT);
        pixel(pixmap, ox, oy, 3, 5, EvoForgePalette.EARTH_DARK);
        pixel(pixmap, ox, oy, 3, 10, EvoForgePalette.EARTH_DARK);
    }

    private static void markerEast(
            Pixmap pixmap,
            int ox,
            int oy) {

        fill(pixmap, ox, oy, 13, 5, 2, 6, EvoForgePalette.OUTLINE);
        fill(pixmap, ox, oy, 12, 6, 1, 4, EvoForgePalette.EARTH_LIGHT);
        pixel(pixmap, ox, oy, 12, 5, EvoForgePalette.EARTH_DARK);
        pixel(pixmap, ox, oy, 12, 10, EvoForgePalette.EARTH_DARK);
    }

    private static float highness(
            int direction,
            int x,
            int y) {

        return switch (direction) {
            case 0 -> (15 - y) / 15f;
            case 1 -> x / 15f;
            case 2 -> y / 15f;
            case 3 -> (15 - x) / 15f;
            default -> throw new IllegalArgumentException(
                    "unknown direction " + direction);
        };
    }

    private static int direction(
            RampShape shape) {

        if (shape == RampShape.POSITIVE_Y) {
            return 0;
        }
        if (shape == RampShape.POSITIVE_X) {
            return 1;
        }
        if (shape == RampShape.NEGATIVE_Y) {
            return 2;
        }
        if (shape == RampShape.NEGATIVE_X) {
            return 3;
        }
        throw new IllegalArgumentException("unsupported RampShape: " + shape);
    }

    private static void bleedPadding(
            Pixmap pixmap,
            int ox,
            int oy) {

        for (int i = 0; i < TILE_PIXELS; i++) {
            pixmap.drawPixel(ox + i, oy - 1, pixmap.getPixel(ox + i, oy));
            pixmap.drawPixel(
                    ox + i,
                    oy + TILE_PIXELS,
                    pixmap.getPixel(ox + i, oy + TILE_PIXELS - 1));
            pixmap.drawPixel(ox - 1, oy + i, pixmap.getPixel(ox, oy + i));
            pixmap.drawPixel(
                    ox + TILE_PIXELS,
                    oy + i,
                    pixmap.getPixel(ox + TILE_PIXELS - 1, oy + i));
        }

        pixmap.drawPixel(ox - 1, oy - 1, pixmap.getPixel(ox, oy));
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
                pixmap.getPixel(ox + TILE_PIXELS - 1, oy + TILE_PIXELS - 1));
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
        pixmap.fillRectangle(ox + x, oy + y, width, height);
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

    private record Origin(
            int x,
            int y) {
    }
}
