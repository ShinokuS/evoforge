package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Generates the canonical early EvoForge landscape tileset in memory.
 *
 * <p>Simulation provides terrain, Shape and XYZ topology; this presentation
 * component turns that topology into deterministic 16x16 pixel art. No image
 * files, asset descriptors or third-party tilesets are required.</p>
 */
public final class ProceduralLandscapePack {

    public static final int TILE_PIXELS = 16;
    public static final int SURFACE_VARIANTS = 4;

    private static final int MASK_COUNT = 256;
    private static final int RAMP_DIRECTIONS = 4;
    private static final int SURFACE_TILE_COUNT =
            MASK_COUNT * SURFACE_VARIANTS;
    private static final int TOTAL_TILE_COUNT =
            SURFACE_TILE_COUNT
                    + RAMP_DIRECTIONS * SURFACE_VARIANTS;

    private static final int PADDING = 1;
    private static final int STRIDE = TILE_PIXELS + PADDING * 2;
    private static final int ATLAS_COLUMNS = 32;
    private static final int ATLAS_ROWS =
            (TOTAL_TILE_COUNT + ATLAS_COLUMNS - 1) / ATLAS_COLUMNS;

    private final Texture texture;
    private final TextureRegion[][] surfaces =
            new TextureRegion[SURFACE_VARIANTS][MASK_COUNT];
    private final TextureRegion[][] ramps =
            new TextureRegion[RAMP_DIRECTIONS][SURFACE_VARIANTS];

    public ProceduralLandscapePack() {
        Pixmap atlas = new Pixmap(
                ATLAS_COLUMNS * STRIDE,
                ATLAS_ROWS * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        generateSurfaceTiles(atlas);
        generateRampTiles(atlas);

        texture = new Texture(atlas);
        texture.setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);
        atlas.dispose();

        indexRegions();
    }

    public TextureRegion surface(
            int topologyMask,
            int variant) {

        return surfaces[Math.floorMod(variant, SURFACE_VARIANTS)]
                [LandscapeTopology.normalize(topologyMask)];
    }

    public TextureRegion ramp(
            int riseX,
            int riseY,
            int variant) {

        return ramps[rampDirection(riseX, riseY)]
                [Math.floorMod(variant, SURFACE_VARIANTS)];
    }

    public void dispose() {
        texture.dispose();
    }

    private static void generateSurfaceTiles(
            Pixmap atlas) {

        for (int variant = 0; variant < SURFACE_VARIANTS; variant++) {
            for (int mask = 0; mask < MASK_COUNT; mask++) {
                int index = variant * MASK_COUNT + mask;
                TileOrigin origin = origin(index);
                drawSurface(
                        atlas,
                        origin.x(),
                        origin.y(),
                        LandscapeTopology.normalize(mask),
                        variant);
                bleedPadding(atlas, origin.x(), origin.y());
            }
        }
    }

    private static void generateRampTiles(
            Pixmap atlas) {

        for (int variant = 0; variant < SURFACE_VARIANTS; variant++) {
            for (int direction = 0; direction < RAMP_DIRECTIONS; direction++) {
                int index = SURFACE_TILE_COUNT
                        + variant * RAMP_DIRECTIONS
                        + direction;
                TileOrigin origin = origin(index);
                drawRamp(
                        atlas,
                        origin.x(),
                        origin.y(),
                        direction,
                        variant);
                bleedPadding(atlas, origin.x(), origin.y());
            }
        }
    }

    private void indexRegions() {
        for (int variant = 0; variant < SURFACE_VARIANTS; variant++) {
            for (int mask = 0; mask < MASK_COUNT; mask++) {
                surfaces[variant][mask] = region(
                        variant * MASK_COUNT + mask);
            }
        }

        for (int variant = 0; variant < SURFACE_VARIANTS; variant++) {
            for (int direction = 0; direction < RAMP_DIRECTIONS; direction++) {
                ramps[direction][variant] = region(
                        SURFACE_TILE_COUNT
                                + variant * RAMP_DIRECTIONS
                                + direction);
            }
        }
    }

    private TextureRegion region(
            int index) {

        TileOrigin origin = origin(index);
        return new TextureRegion(
                texture,
                origin.x(),
                origin.y(),
                TILE_PIXELS,
                TILE_PIXELS);
    }

    private static TileOrigin origin(
            int index) {

        int column = index % ATLAS_COLUMNS;
        int row = index / ATLAS_COLUMNS;
        return new TileOrigin(
                column * STRIDE + PADDING,
                row * STRIDE + PADDING);
    }

    private static void drawSurface(
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
                EvoForgePalette.GRASS_BASE);
        addGrassTexture(pixmap, ox, oy, mask, variant);

        if (!LandscapeTopology.contains(mask, LandscapeTopology.N)) {
            drawNorthEdge(pixmap, ox, oy, variant);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.E)) {
            drawEastEdge(pixmap, ox, oy, variant);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.S)) {
            drawSouthEdge(pixmap, ox, oy, variant);
        }
        if (!LandscapeTopology.contains(mask, LandscapeTopology.W)) {
            drawWestEdge(pixmap, ox, oy, variant);
        }

        drawInnerCorners(pixmap, ox, oy, mask);
    }

    private static void drawNorthEdge(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        fill(pixmap, ox, oy, 0, 0, 16, 1, EvoForgePalette.EARTH_DARK);
        fill(pixmap, ox, oy, 0, 1, 16, 1, EvoForgePalette.EARTH_BASE);
        fill(pixmap, ox, oy, 0, 2, 16, 1, EvoForgePalette.GRASS_LIGHT);
        scatterEarth(pixmap, ox, oy, 0, 0, 16, 2, variant * 31 + 7);
    }

    private static void drawSouthEdge(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        fill(pixmap, ox, oy, 0, 12, 16, 1, EvoForgePalette.GRASS_DARK);
        fill(pixmap, ox, oy, 0, 13, 16, 2, EvoForgePalette.EARTH_BASE);
        fill(pixmap, ox, oy, 0, 15, 16, 1, EvoForgePalette.EARTH_SHADOW);
        scatterEarth(pixmap, ox, oy, 0, 13, 16, 2, variant * 37 + 11);
    }

    private static void drawWestEdge(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        fill(pixmap, ox, oy, 0, 0, 1, 16, EvoForgePalette.EARTH_DARK);
        fill(pixmap, ox, oy, 1, 0, 1, 16, EvoForgePalette.EARTH_BASE);
        fill(pixmap, ox, oy, 2, 0, 1, 16, EvoForgePalette.GRASS_LIGHT);
        scatterEarth(pixmap, ox, oy, 0, 0, 2, 16, variant * 41 + 13);
    }

    private static void drawEastEdge(
            Pixmap pixmap,
            int ox,
            int oy,
            int variant) {

        fill(pixmap, ox, oy, 13, 0, 1, 16, EvoForgePalette.GRASS_DARK);
        fill(pixmap, ox, oy, 14, 0, 1, 16, EvoForgePalette.EARTH_BASE);
        fill(pixmap, ox, oy, 15, 0, 1, 16, EvoForgePalette.EARTH_SHADOW);
        scatterEarth(pixmap, ox, oy, 14, 0, 2, 16, variant * 43 + 17);
    }

    private static void drawInnerCorners(
            Pixmap pixmap,
            int ox,
            int oy,
            int mask) {

        boolean north = LandscapeTopology.contains(mask, LandscapeTopology.N);
        boolean east = LandscapeTopology.contains(mask, LandscapeTopology.E);
        boolean south = LandscapeTopology.contains(mask, LandscapeTopology.S);
        boolean west = LandscapeTopology.contains(mask, LandscapeTopology.W);

        if (north && east
                && !LandscapeTopology.contains(mask, LandscapeTopology.NE)) {
            innerCorner(pixmap, ox, oy, 15, 0, -1, 1);
        }
        if (south && east
                && !LandscapeTopology.contains(mask, LandscapeTopology.SE)) {
            innerCorner(pixmap, ox, oy, 15, 15, -1, -1);
        }
        if (south && west
                && !LandscapeTopology.contains(mask, LandscapeTopology.SW)) {
            innerCorner(pixmap, ox, oy, 0, 15, 1, -1);
        }
        if (north && west
                && !LandscapeTopology.contains(mask, LandscapeTopology.NW)) {
            innerCorner(pixmap, ox, oy, 0, 0, 1, 1);
        }
    }

    private static void innerCorner(
            Pixmap pixmap,
            int ox,
            int oy,
            int cornerX,
            int cornerY,
            int towardX,
            int towardY) {

        pixel(
                pixmap,
                ox,
                oy,
                cornerX,
                cornerY,
                EvoForgePalette.EARTH_DARK);
        pixel(
                pixmap,
                ox,
                oy,
                cornerX + towardX,
                cornerY,
                EvoForgePalette.EARTH_BASE);
        pixel(
                pixmap,
                ox,
                oy,
                cornerX,
                cornerY + towardY,
                EvoForgePalette.EARTH_BASE);
        pixel(
                pixmap,
                ox,
                oy,
                cornerX + towardX,
                cornerY + towardY,
                EvoForgePalette.GRASS_DARK);
    }

    private static void drawRamp(
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
        scatterEarth(
                pixmap,
                ox,
                oy,
                0,
                0,
                TILE_PIXELS,
                TILE_PIXELS,
                101 + direction * 19 + variant * 47);

        for (int y = 0; y < TILE_PIXELS; y++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (rampInside(direction, x, y)) {
                    pixel(
                            pixmap,
                            ox,
                            oy,
                            x,
                            y,
                            EvoForgePalette.GRASS_BASE);
                }
            }
        }

        outlineRampBanks(pixmap, ox, oy, direction);
        drawRampContours(pixmap, ox, oy, direction);
        addRampGrassDetail(pixmap, ox, oy, direction, variant);
    }

    private static void outlineRampBanks(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction) {

        for (int y = 0; y < TILE_PIXELS; y++) {
            for (int x = 0; x < TILE_PIXELS; x++) {
                if (!rampInside(direction, x, y)) {
                    continue;
                }

                boolean boundary = !rampInside(direction, x - 1, y)
                        || !rampInside(direction, x + 1, y)
                        || !rampInside(direction, x, y - 1)
                        || !rampInside(direction, x, y + 1);

                if (boundary) {
                    pixel(
                            pixmap,
                            ox,
                            oy,
                            x,
                            y,
                            rampBoundaryColor(direction, x, y));
                }
            }
        }
    }

    private static boolean rampInside(
            int direction,
            int x,
            int y) {

        if (x < 0 || y < 0 || x >= TILE_PIXELS || y >= TILE_PIXELS) {
            return false;
        }

        float highness = highness(direction, x, y);
        float cross = crossCoordinate(direction, x, y);
        int width = 8 + Math.round(highness * 6f);
        return Math.abs(cross - 7.5f) <= width * 0.5f;
    }

    private static int rampBoundaryColor(
            int direction,
            int x,
            int y) {

        // Fixed world-space light from north-west. The ramp geometry rotates,
        // but lighting does not, preventing the common "rotated shadow" look.
        if (x <= 3 || y <= 3) {
            return EvoForgePalette.GRASS_LIGHT;
        }
        if (x >= 12 || y >= 12) {
            return EvoForgePalette.GRASS_DARK;
        }
        return direction == 0 || direction == 3
                ? EvoForgePalette.GRASS_LIGHT
                : EvoForgePalette.GRASS_DARK;
    }

    private static void drawRampContours(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction) {

        for (int band : new int[] {5, 9, 13}) {
            for (int cross = 3; cross <= 12; cross++) {
                int x;
                int y;

                if (direction == 0 || direction == 2) {
                    x = cross;
                    y = direction == 0
                            ? TILE_PIXELS - 1 - band
                            : band;
                } else {
                    x = direction == 1
                            ? band
                            : TILE_PIXELS - 1 - band;
                    y = cross;
                }

                if (rampInside(direction, x, y)) {
                    pixel(
                            pixmap,
                            ox,
                            oy,
                            x,
                            y,
                            EvoForgePalette.GRASS_DARK);
                }
            }
        }
    }

    private static void addGrassTexture(
            Pixmap pixmap,
            int ox,
            int oy,
            int mask,
            int variant) {

        int state = 0x6D2B79F5 ^ mask * 131 ^ variant * 977;
        for (int i = 0; i < 15; i++) {
            state = mix(state + i * 0x9E3779B9);
            int x = 2 + Math.floorMod(state, 12);
            state = mix(state ^ 0x85EBCA6B);
            int y = 2 + Math.floorMod(state, 12);
            int color = i % 4 == 0
                    ? EvoForgePalette.GRASS_LIGHT
                    : EvoForgePalette.GRASS_DARK;
            pixel(pixmap, ox, oy, x, y, color);

            if (i % 5 == 0 && y > 2) {
                pixel(
                        pixmap,
                        ox,
                        oy,
                        x,
                        y - 1,
                        EvoForgePalette.GRASS_DEEP);
            }
        }
    }

    private static void addRampGrassDetail(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int variant) {

        int state = 0x4F1BBCDC ^ direction * 193 ^ variant * 811;
        for (int i = 0; i < 7; i++) {
            state = mix(state + i * 73);
            int x = 3 + Math.floorMod(state, 10);
            state = mix(state ^ 0x27D4EB2D);
            int y = 3 + Math.floorMod(state, 10);

            if (rampInside(direction, x, y)) {
                pixel(
                        pixmap,
                        ox,
                        oy,
                        x,
                        y,
                        i % 3 == 0
                                ? EvoForgePalette.GRASS_LIGHT
                                : EvoForgePalette.GRASS_DEEP);
            }
        }
    }

    private static void scatterEarth(
            Pixmap pixmap,
            int ox,
            int oy,
            int x,
            int y,
            int width,
            int height,
            int seed) {

        if (width <= 0 || height <= 0) {
            return;
        }

        int state = seed;
        int count = Math.max(2, width * height / 8);
        for (int i = 0; i < count; i++) {
            state = mix(state + i * 31);
            int px = x + Math.floorMod(state, width);
            state = mix(state ^ 0x9E3779B9);
            int py = y + Math.floorMod(state, height);
            pixel(
                    pixmap,
                    ox,
                    oy,
                    px,
                    py,
                    i % 3 == 0
                            ? EvoForgePalette.EARTH_LIGHT
                            : EvoForgePalette.EARTH_DARK);
        }
    }

    private static float highness(
            int direction,
            int x,
            int y) {

        return switch (direction) {
            case 0 -> (TILE_PIXELS - 1 - y) / 15f; // +Y / north
            case 1 -> x / 15f;                     // +X / east
            case 2 -> y / 15f;                     // -Y / south
            case 3 -> (TILE_PIXELS - 1 - x) / 15f; // -X / west
            default -> throw new IllegalArgumentException(
                    "unknown ramp direction " + direction);
        };
    }

    private static float crossCoordinate(
            int direction,
            int x,
            int y) {

        return direction == 0 || direction == 2 ? x : y;
    }

    private static int rampDirection(
            int riseX,
            int riseY) {

        if (riseX == 0 && riseY == 1) {
            return 0;
        }
        if (riseX == 1 && riseY == 0) {
            return 1;
        }
        if (riseX == 0 && riseY == -1) {
            return 2;
        }
        if (riseX == -1 && riseY == 0) {
            return 3;
        }
        throw new IllegalArgumentException(
                "unsupported ramp rise vector " + riseX + "," + riseY);
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

    private record TileOrigin(
            int x,
            int y) {
    }
}
