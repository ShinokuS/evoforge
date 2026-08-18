package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Presentation-only green height palette for terrain surfaces.
 *
 * <p>Sensitivity controls both palette spread and band density. The UI-normalized range intentionally
 * exposes up to twice the original contrast: 50% now reproduces the former 100% look, while 100%
 * emphasizes smaller Z differences more strongly. Zero remains the neutral unmodified midpoint.</p>
 */
public final class TerrainElevationColorRamp {
    public static final int SCALE = 1_000_000;
    public static final int DEFAULT_PREVIEW_SENSITIVITY_PPM = 500_000;
    public static final int DEFAULT_SCENARIO_SENSITIVITY_PPM = 350_000;

    private static final float MAX_CONTRAST = 2f;
    private static final Color LOW = new Color(0.18f, 0.34f, 0.13f, 1f);
    private static final Color MID = new Color(0.50f, 0.67f, 0.34f, 1f);
    private static final Color HIGH = new Color(0.79f, 0.88f, 0.53f, 1f);

    private TerrainElevationColorRamp() {
    }

    /** Returns the normalized palette coordinate consumed by textured terrain presentation. */
    public static float position(
            long elevation,
            long minimum,
            long maximum,
            int sensitivityPpm) {
        validateSensitivity(sensitivityPpm);
        if (sensitivityPpm == 0 || maximum <= minimum) return 0.5f;

        float normalized = MathUtils.clamp(
                (float) (elevation - minimum) / (float) (maximum - minimum),
                0f,
                1f);
        float contrast = sensitivityPpm / (float) SCALE * MAX_CONTRAST;

        int bands = 3 + Math.round(45f * contrast);
        float quantized = Math.round(normalized * (bands - 1f)) / (bands - 1f);
        return MathUtils.clamp(
                0.5f + (quantized - 0.5f) * contrast,
                0f,
                1f);
    }

    /** Direct dark-green to light-green palette used by non-textured previews such as 3D mesh. */
    public static Color color(
            long elevation,
            long minimum,
            long maximum,
            int sensitivityPpm,
            Color out) {
        if (out == null) throw new IllegalArgumentException("output color must not be null");
        float palettePosition = position(elevation, minimum, maximum, sensitivityPpm);

        if (palettePosition <= 0.5f) {
            float local = palettePosition * 2f;
            return out.set(
                    MathUtils.lerp(LOW.r, MID.r, local),
                    MathUtils.lerp(LOW.g, MID.g, local),
                    MathUtils.lerp(LOW.b, MID.b, local),
                    1f);
        }

        float local = (palettePosition - 0.5f) * 2f;
        return out.set(
                MathUtils.lerp(MID.r, HIGH.r, local),
                MathUtils.lerp(MID.g, HIGH.g, local),
                MathUtils.lerp(MID.b, HIGH.b, local),
                1f);
    }

    /** Encodes one palette position into SpriteBatch vertex color for TerrainElevationTintShader. */
    public static Color shaderColor(
            long elevation,
            long minimum,
            long maximum,
            int sensitivityPpm,
            Color out) {
        if (out == null) throw new IllegalArgumentException("output color must not be null");
        float position = position(elevation, minimum, maximum, sensitivityPpm);
        return out.set(position, position, position, 1f);
    }

    private static void validateSensitivity(int sensitivityPpm) {
        if (sensitivityPpm < 0 || sensitivityPpm > SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
    }
}
