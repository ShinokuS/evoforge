package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.visualizer.visual.EvoForgePalette;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;

/** Procedural ramp art with open platform contacts and topology-aware lateral banks. */
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

        // The whole low/high contact is grass. A ramp must meet both connected platforms without
        // any brown separator, including the corner pixels of the contact edge.
        fill(pixmap, ox, oy, 0, 0, TILE, TILE, EvoForgePalette.GRASS_BASE);
        shadeSlope(pixmap, ox, oy, direction);

        boolean positiveJoined = (sideState & POSITIVE_SIDE_JOINED) != 0;
        boolean negativeJoined = (sideState & NEGATIVE_SIDE_JOINED) != 0;
        boolean positiveLowCross = positiveSideIsLowCross(direction);

        if (!positiveJoined) {
            drawLateralBank(pixmap, ox, oy, direction, positiveLowCross);
        }
        if (!negativeJoined) {
            drawLateralBank(pixmap, ox, oy, direction, !positiveLowCross);
        }

        drawSlopeCues(pixmap, ox, oy, direction, sideState);
        addGrassTexture(pixmap, ox, oy, direction, sideState, variant);
    }

    private static void shadeSlope(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction) {
        for (int along = 2; along <= 13; along++) {
            float highness = along / 15f;
            int color = highness < 0.28f
                    ? EvoForgePalette.GRASS_DARK
                    : highness > 0.72f
                            ? EvoForgePalette.GRASS_LIGHT
                            : EvoForgePalette.GRASS_BASE;
            if (color == EvoForgePalette.GRASS_BASE) continue;
            for (int cross = 2; cross <= 13; cross++) {
                if (((along + cross) & 3) != 0) continue;
                setDirectionalPixel(
                        pixmap,
                        ox,
                        oy,
                        direction,
                        along,
                        cross,
                        color);
            }
        }
    }

    /**
     * Draws one exposed side bank only. The first and last two pixels along the rise axis are
     * deliberately untouched so the low/high platform contacts stay completely border-free.
     */
    private static void drawLateralBank(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            boolean lowCrossSide) {
        int outerCross = lowCrossSide ? 0 : TILE - 1;
        int innerCross = lowCrossSide ? 1 : TILE - 2;
        int grassCross = lowCrossSide ? 2 : TILE - 3;

        for (int along = 2; along <= TILE - 3; along++) {
            setDirectionalPixel(
                    pixmap,
                    ox,
                    oy,
                    direction,
                    along,
                    outerCross,
                    EvoForgePalette.EARTH_SHADOW);
            setDirectionalPixel(
                    pixmap,
                    ox,
                    oy,
                    direction,
                    along,
                    innerCross,
                    EvoForgePalette.EARTH_BASE);
            if ((along & 3) == 0) {
                setDirectionalPixel(
                        pixmap,
                        ox,
                        oy,
                        direction,
                        along,
                        innerCross,
                        EvoForgePalette.EARTH_LIGHT);
            }
            setDirectionalPixel(
                    pixmap,
                    ox,
                    oy,
                    direction,
                    along,
                    grassCross,
                    EvoForgePalette.GRASS_DARK);
        }
    }

    private static void drawSlopeCues(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState) {
        boolean positiveJoined = (sideState & POSITIVE_SIDE_JOINED) != 0;
        boolean negativeJoined = (sideState & NEGATIVE_SIDE_JOINED) != 0;
        boolean positiveLowCross = positiveSideIsLowCross(direction);
        boolean lowCrossJoined = positiveLowCross ? positiveJoined : negativeJoined;
        boolean highCrossJoined = positiveLowCross ? negativeJoined : positiveJoined;
        int firstCross = lowCrossJoined ? 1 : 3;
        int lastCross = highCrossJoined ? TILE - 2 : TILE - 4;

        for (int along : new int[] {4, 8, 12}) {
            for (int cross = firstCross; cross <= lastCross; cross++) {
                if ((cross & 1) != 0) continue;
                setDirectionalPixel(
                        pixmap,
                        ox,
                        oy,
                        direction,
                        along,
                        cross,
                        EvoForgePalette.GRASS_DEEP);
            }
        }
    }

    private static void addGrassTexture(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int sideState,
            int variant) {
        int state = 0x4F1BBCDC ^ direction * 193 ^ sideState * 397 ^ variant * 811;
        for (int i = 0; i < 10; i++) {
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
                    i % 4 == 0 ? EvoForgePalette.GRASS_LIGHT : EvoForgePalette.GRASS_DEEP);
        }
    }

    /**
     * Coordinates art in ramp-local axes. Along=0 is the low contact and along=15 is the high
     * contact regardless of cardinal orientation; cross spans the two lateral sides.
     */
    private static void setDirectionalPixel(
            Pixmap pixmap,
            int ox,
            int oy,
            int direction,
            int along,
            int cross,
            int color) {
        int x;
        int y;
        switch (direction) {
            case 0 -> { // +Y: low south, high north
                x = cross;
                y = TILE - 1 - along;
            }
            case 1 -> { // +X: low west, high east
                x = along;
                y = TILE - 1 - cross;
            }
            case 2 -> { // -Y: low north, high south
                x = TILE - 1 - cross;
                y = along;
            }
            case 3 -> { // -X: low east, high west
                x = TILE - 1 - along;
                y = cross;
            }
            default -> throw new IllegalArgumentException("unknown ramp direction " + direction);
        }
        pixel(pixmap, ox, oy, x, y, color);
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
