package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import io.github.evoforge.simulation.world.preparation.PreparedGeneratedWorld;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Optional;

/**
 * Starts simulation from already prepared immutable world data.
 *
 * <p>This class never invokes world generation or calibration. Its responsibilities are one-way
 * materialization of prepared facts into runtime stores, runtime-only atmosphere composition, and
 * starting SimulationAssembly.</p>
 */
public final class GeneratedWorldRuntimeBootstrap {
    private final AtmosphericRuntimePlan atmospherePlan;
    private final Optional<SimulationTimeScale> timeScale;

    public GeneratedWorldRuntimeBootstrap() {
        this(AtmosphericRuntimePlans.climateNormalsCompatibility(), Optional.empty());
    }

    public GeneratedWorldRuntimeBootstrap(AtmosphericRuntimePlan atmospherePlan) {
        this(atmospherePlan, Optional.empty());
    }

    public static GeneratedWorldRuntimeBootstrap withTimeScale(
            SimulationTimeScale timeScale) {
        return withTimeScale(AtmosphericRuntimePlans.climateNormalsCompatibility(), timeScale);
    }

    public static GeneratedWorldRuntimeBootstrap withTimeScale(
            AtmosphericRuntimePlan atmospherePlan,
            SimulationTimeScale timeScale) {
        if (timeScale == null) {
            throw new IllegalArgumentException("simulation time scale must not be null");
        }
        return new GeneratedWorldRuntimeBootstrap(atmospherePlan, Optional.of(timeScale));
    }

    private GeneratedWorldRuntimeBootstrap(
            AtmosphericRuntimePlan atmospherePlan,
            Optional<SimulationTimeScale> timeScale) {
        if (atmospherePlan == null || timeScale == null) {
            throw new IllegalArgumentException("runtime bootstrap dependencies must not be null");
        }
        this.atmospherePlan = atmospherePlan;
        this.timeScale = timeScale;
    }

    public GeneratedWorldRuntime start(
            PreparedGeneratedWorld prepared,
            SimulationAssembly assembly,
            TerrainMaterialBindings bindings) {
        if (prepared == null || assembly == null || bindings == null) {
            throw new IllegalArgumentException("runtime bootstrap inputs must not be null");
        }
        return start(
                prepared.atlas(),
                assembly,
                TerrainMaterialResolver.resolved(prepared.terrainMaterials(), bindings));
    }

    /** Specialized path for callers that intentionally own stable-material resolution. */
    public GeneratedWorldRuntime start(
            WorldAtlas atlas,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        if (atlas == null || assembly == null || materials == null) {
            throw new IllegalArgumentException("runtime bootstrap inputs must not be null");
        }

        WorldBounds bounds = atlas.genesis().spec().bounds();
        assembly.worldBounds(
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());

        atlas.genesis().spec().physicalSpaceScale().ifPresent(scale ->
                assembly.physicalCellVolumeMilliliters(
                        scale.physicalCellVolumeExact().millilitersPerFullCell()));

        TerrainMaterializationResult materialization = assembly.materializeGeneratedTerrain(
                atlas.elevation(), materials);
        materializeInitialSurfaceWater(atlas, assembly);

        AtmosphericRuntimeComposition atmosphere = atmospherePlan.compose(atlas, timeScale);
        atmosphere.waterForcing().ifPresent(assembly::atmosphericWaterForcing);

        SimulationRuntime runtime = assembly.start();
        return new GeneratedWorldRuntime(
                atlas,
                materialization,
                runtime,
                timeScale,
                atmosphere.weatherState());
    }

    private static void materializeInitialSurfaceWater(
            WorldAtlas atlas,
            SimulationAssembly assembly) {
        SurfaceHydrologyField hydrology = atlas.surfaceHydrology();
        WorldBounds bounds = hydrology.bounds();
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                int amount = hydrology.initialWaterVolumeAt(worldX, worldY);
                if (amount == 0) continue;
                int waterZ = Math.addExact(atlas.elevation().elevationAt(worldX, worldY), 1);
                if (waterZ > bounds.maxZ()) {
                    throw new IllegalStateException(
                            "generated surface Water has no open cell above terrain at ("
                                    + worldX + ", " + worldY + ")");
                }
                assembly.initialWater(worldX, worldY, waterZ, amount);
            }
        }
    }
}
