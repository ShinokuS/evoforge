package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.visualizer.visual.EvoForgePalette;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;

/** Procedural ramp art with readable slope banks and topology-aware seamless neighbours. */
final class ProceduralRampArt {
    private static final int TILE = ProceduralLandscapePack.TILE_PIXELS;
    private static final int DIRECTIONS = 4;
    private static final int SIDE_STATES = 4;
    private static final int VARIANTS = ProceduralLandscapePack.SURFACE_VARIANTS;
    private static final int PADDING = 1;
    private static final int STRIDE = TILE + PADDING * 2;
    private static final int COLUMNS = 16;
    private static final int TILE_COUNT = DIRECTIONS * SIDE_STATES * VARIANTS;
    private static final int ROWS = (TILE_COUNT + COLUMNS - 1) / COLUMNS;

    private static final int POSITIVE_SIDE_JOINED = 1;
    private static final int NEGATIVE_SIDE_JOINED = 1 << 1;

    private final Texture texture;
    private final TextureRegion[][][] regions =
            new TextureRegion[DIRECTIONS][SIDE_STATES][VARIANTS];

    ProceduralRampArt() {
        Pixmap atlas = new Pixmap(
                COLUMNS * STRIDE,
                ROWS * STRIDE,
                Pixmap.Format.RGBA8888);
        atlas.setColor(0x00000000);
        atlas.fill();

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int sideState = 0; sideState < SIDE_STATES; sideState++) {
                for (int variant = 0; variant < VARIANTS; variant++) {
                    int index = index(direction, sideState, variant);
                    int ox = (index % COLUMNS) * STRIDE + PADDING;
                    int oy = (index / COLUMNS) * STRIDE + PADDING;
                    draw(atlas, ox, oy, direction, sideState, variant);
                    bleed(atlas, ox, oy);
                }
            }
        }

        texture = new Texture(atlas);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        atlas.dispose();

        for (int direction = 0; direction < DIRECTIONS; direction++) {
            for (int sideState = 0; sideState < SIDE_STATES; sideState++) {
                for (int variant = 0; variant < VARIANTS; variant++) {
                    int index = index(direction, sideState, variant);
                    regions[direction][sideState][variant] = new TextureRegion(
                            texture,
                            (index % COLUMNS) * STRIDE + PADDING,
                            (index / COLUMNS) * STRIDE + PADDING,
                            TILE,
                            TILE);
                }
            }
        }
    }

    TextureRegion region(int riseX, int riseY, int topologyMask, int variant) {
        int direction = direction(riseX, riseY);
        int sideState = sideState(direction, LandscapeTopology.normalize(topologyMask));
        return regions[direction][sideState][Math.floorMod(variant, VARIANTS)];
    }

    void dispose() {
        texture.dispose();
    }

    private static void draw(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState,
            int variant) {
        // Restore the original readable ramp language: exposed earth banks around a grassy slope.
        // Unlike the old tile, however, the bank exists only on lateral sides that are actually
        // exposed. Low/high contacts never receive a contour and joined parallel ramps erase the
        // shared bank completely.
        fill(pixmap, ox, oy, 0, 0, TILE, TILE, EvoForgePalette.EARTH_BASE);
        scatterEarth(
                pixmap,
                ox,
                oy,
                0,
                0,
                TILE,
                TILE,
                101 + direction * 19 + sideState * 131 + variant * 47);

        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                if (rampInside(direction, sideState, x, y)) {
                    pixel(pixmap, ox, oy, x, y, EvoForgePalette.GRASS_BASE);
                }
            }
        }

        outlineLateralBanks(pixmap, ox, oy, direction, sideState);
        drawRampContours(pixmap, ox, oy, direction, sideState);
        addRampGrassDetail(pixmap, ox, oy, direction, sideState, variant);
    }

    private static void outlineLateralBanks(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState) {
        for (int y = 0; y < TILE; y++) {
            for (int x = 0; x < TILE; x++) {
                if (!rampInside(direction, sideState, x, y)) continue;

                // Only the two faces perpendicular to the rise axis are banks. Looking outside the
                // tile along the low/high axis used to create the unwanted line across every ramp
                // contact, so that axis intentionally does not participate here.
                boolean boundary = direction == 0 || direction == 2
                        ? !rampInside(direction, sideState, x - 1, y)
                                || !rampInside(direction, sideState, x + 1, y)
                        : !rampInside(direction, sideState, x, y - 1)
                                || !rampInside(direction, sideState, x, y + 1);
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
            int sideState,
            int x,
            int y) {
        if (x < 0 || y < 0 || x >= TILE || y >= TILE) return false;

        float highness = highness(direction, x, y);
        float cross = crossCoordinate(direction, x, y);
        int width = 8 + Math.round(highness * 6f);
        float low = 7.5f - width * 0.5f;
        float high = 7.5f + width * 0.5f;

        boolean positiveJoined = (sideState & POSITIVE_SIDE_JOINED) != 0;
        boolean negativeJoined = (sideState & NEGATIVE_SIDE_JOINED) != 0;
        if (positiveSideIsLowCross(direction)) {
            if (positiveJoined) low = 0f;
            if (negativeJoined) high = TILE - 1f;
        } else {
            if (positiveJoined) high = TILE - 1f;
            if (negativeJoined) low = 0f;
        }
        return cross >= low && cross <= high;
    }

    private static int rampBoundaryColor(int direction, int x, int y) {
        // Fixed world-space light from north-west. The ramp rotates, lighting does not.
        if (x <= 3 || y <= 3) return EvoForgePalette.GRASS_LIGHT;
        if (x >= 12 || y >= 12) return EvoForgePalette.GRASS_DARK;
        return direction == 0 || direction == 3
                ? EvoForgePalette.GRASS_LIGHT
                : EvoForgePalette.GRASS_DARK;
    }

    private static void drawRampContours(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState) {
        for (int band : new int[] {5, 9, 13}) {
            for (int cross = 3; cross <= 12; cross++) {
                int x;
                int y;
                if (direction == 0 || direction == 2) {
                    x = cross;
                    y = direction == 0 ? TILE - 1 - band : band;
                } else {
                    x = direction == 1 ? band : TILE - 1 - band;
                    y = cross;
                }
                if (rampInside(direction, sideState, x, y)) {
                    pixel(pixmap, ox, oy, x, y, EvoForgePalette.GRASS_DARK);
                }
            }
        }
    }

    private static void addRampGrassDetail(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState,
            int variant) {
        int state = 0x4F1BBCDC ^ direction * 193 ^ sideState * 397 ^ variant * 811;
        for (int i = 0; i < 7; i++) {
            state = mix(state + i * 73);
            int x = 3 + Math.floorMod(state, 10);
            state = mix(state ^ 0x27D4EB2D);
            int y = 3 + Math.floorMod(state, 10);
            if (rampInside(direction, sideState, x, y)) {
                pixel(
                        pixmap,
                        ox,
                        oy,
                        x,
                        y,
                        i % 3 == 0 ? EvoForgePalette.GRASS_LIGHT : EvoForgePalette.GRASS_DEEP);
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
        if (width <= 0 || height <= 0) return;
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
                    i % 3 == 0 ? EvoForgePalette.EARTH_LIGHT : EvoForgePalette.EARTH_DARK);
        }
    }

    private static float highness(int direction, int x, int y) {
        return switch (direction) {
            case 0 -> (TILE - 1 - y) / (float) (TILE - 1); // +Y / north
            case 1 -> x / (float) (TILE - 1);              // +X / east
            case 2 -> y / (float) (TILE - 1);              // -Y / south
            case 3 -> (TILE - 1 - x) / (float) (TILE - 1);// -X / west
            default -> throw new IllegalArgumentException("unknown ramp direction " + direction);
        };
    }

    private static float crossCoordinate(int direction, int x, int y) {
        return direction == 0 || direction == 2 ? x : y;
    }

    private static boolean positiveSideIsLowCross(int direction) {
        return direction == 0 || direction == 1;
    }

    private static int sideState(int direction, int topologyMask) {
        int riseX = riseX(direction);
        int riseY = riseY(direction);
        int sideX = -riseY;
        int sideY = riseX;
        int state = 0;
        if (LandscapeTopology.contains(topologyMask, topologyBit(sideX, sideY))) {
            state |= POSITIVE_SIDE_JOINED;
        }
        if (LandscapeTopology.contains(topologyMask, topologyBit(-sideX, -sideY))) {
            state |= NEGATIVE_SIDE_JOINED;
        }
        return state;
    }

    private static int topologyBit(int x, int y) {
        if (x == 0 && y == 1) return LandscapeTopology.N;
        if (x == 1 && y == 0) return LandscapeTopology.E;
        if (x == 0 && y == -1) return LandscapeTopology.S;
        if (x == -1 && y == 0) return LandscapeTopology.W;
        throw new IllegalArgumentException("topology side must be cardinal: " + x + "," + y);
    }

    private static int riseX(int direction) {
        return switch (direction) {
            case 0, 2 -> 0;
            case 1 -> 1;
            case 3 -> -1;
            default -> throw new IllegalArgumentException("unknown ramp direction " + direction);
        };
    }

    private static int riseY(int direction) {
        return switch (direction) {
            case 0 -> 1;
            case 1, 3 -> 0;
            case 2 -> -1;
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

    private static int index(int direction, int sideState, int variant) {
        return (direction * SIDE_STATES + sideState) * VARIANTS + variant;
    }

    private static void bleed(Pixmap p, int ox, int oy) {
        for (int i = 0; i < TILE; i++) {
            p.drawPixel(ox + i, oy - 1, p.getPixel(ox + i, oy));
            p.drawPixel(ox + i, oy + TILE, p.getPixel(ox + i, oy + TILE - 1));
            p.drawPixel(ox - 1, oy + i, p.getPixel(ox, oy + i));
            p.drawPixel(ox + TILE, oy + i, p.getPixel(ox + TILE - 1, oy + i));
        }
        p.drawPixel(ox - 1, oy - 1, p.getPixel(ox, oy));
        p.drawPixel(ox + TILE, oy - 1, p.getPixel(ox + TILE - 1, oy));
        p.drawPixel(ox - 1, oy + TILE, p.getPixel(ox, oy + TILE - 1));
        p.drawPixel(ox + TILE, oy + TILE, p.getPixel(ox + TILE - 1, oy + TILE - 1));
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
