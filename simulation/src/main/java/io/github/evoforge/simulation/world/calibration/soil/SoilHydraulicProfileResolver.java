package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Replaceable pre-runtime resolution from authored soil archetypes to physical hydraulic profiles.
 *
 * <p>This deliberately lives after Definition compilation. A future world soil-development stage
 * may replace this material-level resolver with spatial generated properties without changing the
 * authored schema or runtime hydraulic contract.</p>
 */
public final class SoilHydraulicProfileResolver {
    private final SoilCompositionCompiler compositionCompiler;
    private final SoilHydraulicCalibrator hydraulicCalibrator;

    public SoilHydraulicProfileResolver(
            SoilCompositionCompiler compositionCompiler,
            SoilHydraulicCalibrator hydraulicCalibrator) {
        if (compositionCompiler == null || hydraulicCalibrator == null) {
            throw new IllegalArgumentException("soil profile resolver dependencies must not be null");
        }
        this.compositionCompiler = compositionCompiler;
        this.hydraulicCalibrator = hydraulicCalibrator;
    }

    public static SoilHydraulicProfileResolver standard() {
        return new SoilHydraulicProfileResolver(
                new ContinuousSoilCompositionCompiler(SoilCompositionCalibration.representative()),
                new SaxtonRawls2006SoilHydraulicCalibrator());
    }

    public SoilHydraulicProfileBindings resolve(SoilSemanticProfileBindings semantics) {
        if (semantics == null) {
            throw new IllegalArgumentException("soil semantic bindings must not be null");
        }
        Map<TerrainMaterialKey, SoilHydraulicProfile> resolved = new LinkedHashMap<>();
        for (Map.Entry<TerrainMaterialKey, SoilSemanticProfile> entry
                : semantics.asMap().entrySet()) {
            SoilCompositionProfile composition = compositionCompiler.compile(entry.getValue());
            if (composition == null) {
                throw new IllegalStateException("soil composition compiler returned null");
            }
            SoilHydraulicProfile hydraulics = hydraulicCalibrator.calibrate(composition);
            if (hydraulics == null) {
                throw new IllegalStateException("soil hydraulic calibrator returned null");
            }
            resolved.put(entry.getKey(), hydraulics);
        }
        return SoilHydraulicProfileBindings.of(resolved);
    }
}
