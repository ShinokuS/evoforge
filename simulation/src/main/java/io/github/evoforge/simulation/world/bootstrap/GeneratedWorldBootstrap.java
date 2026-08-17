package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.climate.ClimateHydroForcingView;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;

/**
 * One-shot production composition path from immutable genesis provenance into a started runtime.
 *
 * <p>Content composition stays explicit: callers prepare the {@link SimulationAssembly} with the
 * definitions/mechanics their content pack supplies. Generated causal facts choose material keys;
 * explicit runtime bindings map those stable identities into Landscape ids at materialization.
 * Specialized callers may still provide a raw resolver. This bootstrap owns neither authored-data
 * parsing nor runtime world state.</p>
 */
public final class GeneratedWorldBootstrap {
    private final WorldAtlasGenerator atlasGenerator;
    private final TerrainMaterialGenerator terrainMaterialGenerator;

    public GeneratedWorldBootstrap() {
        this(new WorldAtlasGenerator(), new TerrainMaterialGenerationStage());
    }

    public GeneratedWorldBootstrap(WorldAtlasGenerator atlasGenerator) {
        this(atlasGenerator, new TerrainMaterialGenerationStage());
    }

    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        if (atlasGenerator == null || terrainMaterialGenerator == null) {
            throw new IllegalArgumentException("generated world generators must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.terrainMaterialGenerator = terrainMaterialGenerator;
    }

    /** Production path with generated climate connected to ordinary runtime atmosphere systems. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            CompiledTerrainProfile profile,
            TerrainMaterialBindings bindings) {
        return create(genesis, assembly, profile, bindings, true);
    }

    /**
     * Starts the same generated world without attaching atmospheric rain/evaporation processes.
     * Climate facts remain present in the returned Atlas and are not rewritten to zero values.
     */
    public GeneratedWorldRuntime createWithoutAtmosphericForcing(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            CompiledTerrainProfile profile,
            TerrainMaterialBindings bindings) {
        return create(genesis, assembly, profile, bindings, false);
    }

    private GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            CompiledTerrainProfile profile,
            TerrainMaterialBindings bindings,
            boolean attachAtmosphericForcing) {
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
                TerrainMaterialResolver.resolved(materials, bindings),
                attachAtmosphericForcing);
    }

    /** Compatibility/custom path with generated climate connected to runtime atmosphere. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        return create(genesis, assembly, materials, true);
    }

    /**
     * Custom material-resolution path that preserves climate facts while leaving atmosphere off.
     */
    public GeneratedWorldRuntime createWithoutAtmosphericForcing(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        return create(genesis, assembly, materials, false);
    }

    private GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials,
            boolean attachAtmosphericForcing) {
        if (genesis == null || assembly == null || materials == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }
        return start(
                atlasGenerator.generate(genesis),
                assembly,
                materials,
                attachAtmosphericForcing);
    }

    private GeneratedWorldRuntime start(
            WorldAtlas atlas,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials,
            boolean attachAtmosphericForcing) {
        WorldBounds bounds = atlas.genesis().spec().bounds();
        assembly.worldBounds(
                bounds.minX(), bounds.maxX(),
                bounds.minY(), bounds.maxY(),
                bounds.minZ(), bounds.maxZ());

        TerrainMaterializationResult materialization = assembly.materializeGeneratedTerrain(
                atlas.elevation(),
                materials);
        materializeInitialSurfaceWater(atlas, assembly);
        if (attachAtmosphericForcing) {
            assembly.generatedHydroClimate(new ClimateHydroForcingView(atlas.climateNormals()));
        }

        SimulationRuntime runtime = assembly.start();
        return new GeneratedWorldRuntime(atlas, materialization, runtime);
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
