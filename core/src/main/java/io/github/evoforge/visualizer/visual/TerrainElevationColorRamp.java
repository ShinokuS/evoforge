package io.github.evoforge.visualizer.visual;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Presentation-only green height map for terrain surfaces.
 *
 * <p>Sensitivity controls two things together: how much of the dark-to-light palette is used
 * and how many distinct elevation bands are visible. At zero, every elevation uses the neutral
 * grass tone. At maximum, the complete palette is used with fine bands so small Z changes are
 * visually obvious without changing simulation state.</p>
 */
public final class TerrainElevationColorRamp {
    public static final int SCALE = 1_000_000;
    public static final int DEFAULT_PREVIEW_SENSITIVITY_PPM = 650_000;
    public static final int DEFAULT_SCENARIO_SENSITIVITY_PPM = 700_000;

    private static final Color LOW = new Color(0.20f, 0.34f, 0.16f, 1f);
    private static final Color MID = new Color(0.44f, 0.57f, 0.31f, 1f);
    private static final Color HIGH = new Color(0.72f, 0.82f, 0.47f, 1f);

    private TerrainElevationColorRamp() {
    }

    public static Color color(
            long elevation,
            long minimum,
            long maximum,
            int sensitivityPpm,
            Color out) {
        if (out == null) throw new IllegalArgumentException("output color must not be null");
        if (sensitivityPpm < 0 || sensitivityPpm > SCALE) {
            throw new IllegalArgumentException("elevation color sensitivity must be normalized ppm");
        }
        if (sensitivityPpm == 0 || maximum <= minimum) return out.set(MID);

        float normalized = MathUtils.clamp(
                (float) (elevation - minimum) / (float) (maximum - minimum),
                0f,
                1f);
        float sensitivity = sensitivityPpm / (float) SCALE;

        int bands = 2 + Math.round(30f * sensitivity);
        float quantized = Math.round(normalized * (bands - 1f)) / (bands - 1f);
        float palettePosition = 0.5f + (quantized - 0.5f) * sensitivity;

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
}
