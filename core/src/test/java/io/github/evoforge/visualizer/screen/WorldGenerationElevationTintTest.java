package io.github.evoforge.visualizer.screen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import org.junit.jupiter.api.Test;

final class WorldGenerationElevationTintTest {
    private static final WorldGenerationElevationRange RANGE =
            new WorldGenerationElevationRange(0L, 10L * ElevationField.SUBUNITS_PER_CELL);
    private static final WorldGenerationElevationRange RANGE_WITH_WATER =
            new WorldGenerationElevationRange(
                    0L,
                    10L * ElevationField.SUBUNITS_PER_CELL,
                    -20L * ElevationField.SUBUNITS_PER_CELL);

    @Test
    void zeroSensitivityUsesOneNeutralColorAcrossZ() {
        Color low = WorldGenerationElevationTint.color(0L, RANGE, 0, new Color());
        Color high = WorldGenerationElevationTint.color(
                8L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                0,
                new Color());

        assertEquals(Color.rgba8888(low), Color.rgba8888(high));
    }

    @Test
    void halfControlRangeAlreadyUsesTheFormerFullPaletteSpread() {
        Color low = WorldGenerationElevationTint.shaderColor(
                0L, RANGE, 500_000, new Color());
        Color high = WorldGenerationElevationTint.shaderColor(
                RANGE.maximumSubunits(), RANGE, 500_000, new Color());

        assertEquals(0f, low.r, 0.0001f);
        assertEquals(1f, high.r, 0.0001f);
    }

    @Test
    void upperHalfOfControlRangeIncreasesContrastForIntermediateHeights() {
        long quarterHeight = RANGE.maximumSubunits() / 4L;
        Color half = WorldGenerationElevationTint.shaderColor(
                quarterHeight, RANGE, 500_000, new Color());
        Color full = WorldGenerationElevationTint.shaderColor(
                quarterHeight, RANGE, 1_000_000, new Color());

        assertTrue(full.r < half.r, "100% must push low-intermediate terrain darker than 50%");
    }

    @Test
    void maximumSensitivityMapsLowAndHighLandToClearlyDifferentGreenShades() {
        Color low = WorldGenerationElevationTint.color(
                0L,
                RANGE,
                WorldGenerationElevationTint.SCALE,
                new Color());
        Color high = WorldGenerationElevationTint.color(
                10L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                WorldGenerationElevationTint.SCALE,
                new Color());

        assertTrue(low.g < high.g);
        assertTrue(low.r < high.r);
        assertTrue(low.g > low.r, "low color must remain green, not grayscale shading");
        assertTrue(high.g > high.r, "high color must remain green, not grayscale shading");
        assertTrue(high.r - low.r > 0.45f, "full palette should visibly separate low and high land");
    }

    @Test
    void strongerSensitivitySeparatesTheSameZDifferenceMoreClearly() {
        Color lowWeak = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                200_000,
                new Color());
        Color highWeak = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                200_000,
                new Color());
        Color lowStrong = WorldGenerationElevationTint.color(
                2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                1_000_000,
                new Color());
        Color highStrong = WorldGenerationElevationTint.color(
                3L * ElevationField.SUBUNITS_PER_CELL,
                RANGE,
                1_000_000,
                new Color());

        float weakDifference = colorDistance(lowWeak, highWeak);
        float strongDifference = colorDistance(lowStrong, highStrong);
        assertTrue(strongDifference > weakDifference);
    }

    @Test
    void texturedTerrainUsesNeutralShaderColorAtZeroSensitivity() {
        Color low = WorldGenerationElevationTint.shaderColor(0L, RANGE, 0, new Color());
        Color high = WorldGenerationElevationTint.shaderColor(
                RANGE.maximumSubunits(),
                RANGE,
                0,
                new Color());

        assertEquals(0.5f, low.r, 0.0001f);
        assertEquals(Color.rgba8888(low), Color.rgba8888(high));
    }

    @Test
    void submergedShaderExtendsBelowTheExistingLandFloorInsteadOfResettingToNeutral() {
        Color landFloor = WorldGenerationElevationTint.shaderColor(
                0L,
                RANGE_WITH_WATER,
                500_000,
                new Color());
        Color shallow = WorldGenerationElevationTint.shaderColor(
                -2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());
        Color deep = WorldGenerationElevationTint.shaderColor(
                -18L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());

        assertEquals(landFloor.r, shallow.r, 0.0001f);
        assertEquals(shallow.r, deep.r, 0.0001f);
        assertTrue(shallow.b > shallow.r, "negative Z must encode extra darkening below the land floor");
        assertTrue(deep.b > shallow.b, "deeper negative Z must encode stronger extra darkening");
    }

    @Test
    void submerged3DColorStartsAtTheLowestLandShadeAndOnlyDarkensWithDepth() {
        Color landFloor = WorldGenerationElevationTint.color(
                0L,
                RANGE_WITH_WATER,
                500_000,
                new Color());
        Color shallow = WorldGenerationElevationTint.color(
                -2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());
        Color deep = WorldGenerationElevationTint.color(
                -18L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());

        assertNotBrighter(shallow, landFloor);
        assertNotBrighter(deep, shallow);
        assertTrue(deep.r < shallow.r);
        assertTrue(deep.g < shallow.g);
        assertTrue(deep.b < shallow.b);
    }

    @Test
    void shallowNegativeZIsNotBrighterThanHigherPositiveLand() {
        Color underwater = WorldGenerationElevationTint.color(
                -1L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());
        Color higherLand = WorldGenerationElevationTint.color(
                1L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                500_000,
                new Color());

        assertTrue(luminance(underwater) <= luminance(higherLand));
    }

    @Test
    void zeroSensitivityLeavesSubmergedDepthAtTheSameNeutralSurfaceShade() {
        Color land = WorldGenerationElevationTint.color(
                0L,
                RANGE_WITH_WATER,
                0,
                new Color());
        Color shallow = WorldGenerationElevationTint.color(
                -2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                0,
                new Color());
        Color deep = WorldGenerationElevationTint.color(
                -18L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                0,
                new Color());
        Color landShader = WorldGenerationElevationTint.shaderColor(
                0L,
                RANGE_WITH_WATER,
                0,
                new Color());
        Color shallowShader = WorldGenerationElevationTint.shaderColor(
                -2L * ElevationField.SUBUNITS_PER_CELL,
                RANGE_WITH_WATER,
                0,
                new Color());

        assertEquals(Color.rgba8888(land), Color.rgba8888(shallow));
        assertEquals(Color.rgba8888(shallow), Color.rgba8888(deep));
        assertEquals(Color.rgba8888(landShader), Color.rgba8888(shallowShader));
    }

    private static void assertNotBrighter(Color darker, Color lighter) {
        assertTrue(darker.r <= lighter.r + 0.0001f);
        assertTrue(darker.g <= lighter.g + 0.0001f);
        assertTrue(darker.b <= lighter.b + 0.0001f);
    }

    private static float luminance(Color color) {
        return color.r * 0.2126f + color.g * 0.7152f + color.b * 0.0722f;
    }

    private static float colorDistance(Color first, Color second) {
        return Math.abs(second.g - first.g)
                + Math.abs(second.r - first.r)
                + Math.abs(second.b - first.b);
    }
}
