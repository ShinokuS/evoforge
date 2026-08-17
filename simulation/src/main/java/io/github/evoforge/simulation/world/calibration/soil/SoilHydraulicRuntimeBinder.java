package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.Map;

/**
 * One-way pre-start bridge from calibrated physical Soil facts to runtime Landscape definitions.
 *
 * <p>This binder does not calibrate and is never retained by runtime. Missing material bindings are
 * rejected explicitly; terrain materials with no hydraulic profile are intentionally non-soil.</p>
 */
public final class SoilHydraulicRuntimeBinder {
    private SoilHydraulicRuntimeBinder() { }

    public static void bind(
            SimulationAssembly assembly,
            TerrainMaterialBindings terrainMaterials,
            SoilHydraulicProfileBindings hydraulics,
            PhysicalSpaceScale spaceScale,
            SimulationTimeScale timeScale) {
        if (assembly == null
                || terrainMaterials == null
                || hydraulics == null
                || spaceScale == null
                || timeScale == null) {
            throw new IllegalArgumentException("soil hydraulic runtime binding inputs must not be null");
        }

        for (Map.Entry<TerrainMaterialKey, SoilHydraulicProfile> entry
                : hydraulics.asMap().entrySet()) {
            LandscapeDefinitionId definitionId = terrainMaterials.resolve(entry.getKey());
            if (definitionId == null) {
                throw new IllegalArgumentException(
                        "calibrated soil material has no runtime landscape binding: " + entry.getKey());
            }
            SoilProperties compiled = SoilHydraulicRuntimeCompiler.compile(
                    entry.getValue(), spaceScale, timeScale);
            assembly.soilProperties(
                    definitionId,
                    compiled.capacity(),
                    compiled.permeability());
        }
    }
}
