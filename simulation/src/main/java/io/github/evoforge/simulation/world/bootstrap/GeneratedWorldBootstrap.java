package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.climate.ClimateHydroForcingView;
import io.github.evoforge.simulation.world.climate.ClimateWaterNormal;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import java.util.Optional;

/**
 * One-shot production composition path from immutable genesis provenance into a started runtime.
 *
 * <p>Content composition stays explicit: callers prepare the {@link SimulationAssembly} with the
 * definitions/mechanics their content pack supplies. Generated causal facts choose material keys;
 * explicit runtime bindings map those stable identities into Landscape ids at materialization.
 * Specialized callers may still provide a raw resolver. This bootstrap owns neither authored-data
 * parsing nor runtime world state.</p>
 *
 * <p>Generated climate is always part of the Atlas. Whether its current hydrologic projection
 * participates in runtime is selected independently through {@link AtmosphericForcingPolicy}. V8
 * physical climate needs both physical world geometry and a physical tick duration only when that
 * atmospheric projection is actually installed.</p>
 */
public final class GeneratedWorldBootstrap {
    private final WorldAtlasGenerator atlasGenerator;
    private final TerrainMaterialGenerator terrainMaterialGenerator;
    private final AtmosphericForcingPolicy atmosphericForcingPolicy;
    private final Optional<SimulationTimeScale> timeScale;

    public GeneratedWorldBootstrap() {
        this(
                new WorldAtlasGenerator(),
                new TerrainMaterialGenerationStage(),
                AtmosphericForcingPolicy.CLIMATE_NORMALS,
                Optional.empty());
    }

    public GeneratedWorldBootstrap(WorldAtlasGenerator atlasGenerator) {
        this(
                atlasGenerator,
                new TerrainMaterialGenerationStage(),
                AtmosphericForcingPolicy.CLIMATE_NORMALS,
                Optional.empty());
    }

    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        this(
                atlasGenerator,
                new TerrainMaterialGenerationStage(),
                atmosphericForcingPolicy,
                Optional.empty());
    }

    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        this(
                atlasGenerator,
                terrainMaterialGenerator,
                AtmosphericForcingPolicy.CLIMATE_NORMALS,
                Optional.empty());
    }

    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        this(
                atlasGenerator,
                terrainMaterialGenerator,
                atmosphericForcingPolicy,
                Optional.empty());
    }

    public static GeneratedWorldBootstrap withTimeScale(SimulationTimeScale timeScale) {
        return withTimeScale(
                new WorldAtlasGenerator(),
                new TerrainMaterialGenerationStage(),
                AtmosphericForcingPolicy.CLIMATE_NORMALS,
                timeScale);
    }

    public static GeneratedWorldBootstrap withTimeScale(
            WorldAtlasGenerator atlasGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            SimulationTimeScale timeScale) {
        return withTimeScale(
                atlasGenerator,
                new TerrainMaterialGenerationStage(),
                atmosphericForcingPolicy,
                timeScale);
    }

    public static GeneratedWorldBootstrap withTimeScale(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            SimulationTimeScale timeScale) {
        if (timeScale == null) {
            throw new IllegalArgumentException("simulation time scale must not be null");
        }
        return new GeneratedWorldBootstrap(
                atlasGenerator,
                terrainMaterialGenerator,
                atmosphericForcingPolicy,
                Optional.of(timeScale));
    }

    private GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            Optional<SimulationTimeScale> timeScale) {
        if (atlasGenerator == null
                || terrainMaterialGenerator == null
                || atmosphericForcingPolicy == null
                || timeScale == null) {
            throw new IllegalArgumentException("generated world bootstrap dependencies must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.terrainMaterialGenerator = terrainMaterialGenerator;
        this.atmosphericForcingPolicy = atmosphericForcingPolicy;
        this.timeScale = timeScale;
    }

    /** Production path from compiled terrain semantics into authoritative runtime Landscape. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            CompiledTerrainProfile profile,
            TerrainMaterialBindings bindings) {
        if (genesis == null || assembly == null || profile == null || bindings == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }

        WorldAtlas atlas = atlasGenerator.generate(genesis);
        TerrainMaterialField materials = terrainMaterialGenerator.generate(
                atlas.elevation(),
                atlas.geology(),
                atlas.drainage(),
                atlas.surfaceHydrology(),
                profile);
        return start(
                atlas,
                assembly,
                TerrainMaterialResolver.resolved(materials, bindings));
    }

    /** Compatibility/custom path for callers that intentionally own terrain material resolution. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        if (genesis == null || assembly == null || materials == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }
        return start(atlasGenerator.generate(genesis), assembly, materials);
    }

    private GeneratedWorldRuntime start(
            WorldAtlas atlas,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        WorldBounds bounds = atlas.genesis().spec().bounds();
        assembly.worldBounds(
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());

        atlas.genesis().spec().physicalSpaceScale().ifPresent(scale ->
                assembly.physicalCellVolumeMilliliters(
                        scale.physicalCellVolumeExact().millilitersPerFullCell()));

        TerrainMaterializationResult materialization = assembly.materializeGeneratedTerrain(
                atlas.elevation(),
                materials);
        materializeInitialSurfaceWater(atlas, assembly);
        if (AtmosphericForcingPolicy.CLIMATE_NORMALS.equals(atmosphericForcingPolicy)) {
            assembly.generatedHydroClimate(runtimeClimateForcing(atlas));
        }

        SimulationRuntime runtime = assembly.start();
        return new GeneratedWorldRuntime(atlas, materialization, runtime, timeScale);
    }

    private ClimateHydroForcingView runtimeClimateForcing(WorldAtlas atlas) {
        if (!ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME.equals(
                atlas.climateNormals().waterNormalKind())) {
            return new ClimateHydroForcingView(atlas.climateNormals());
        }
        PhysicalSpaceScale spaceScale = atlas.genesis().spec().requirePhysicalSpaceScale();
        SimulationTimeScale physicalTime = timeScale.orElseThrow(() -> new IllegalStateException(
                "physical climate forcing requires an explicit simulation time scale"));
        return new ClimateHydroForcingView(atlas.climateNormals(), spaceScale, physicalTime);
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
