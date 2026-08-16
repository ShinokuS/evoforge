package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
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
 * definitions/mechanics their content pack supplies. A compiled terrain profile provides validated
 * reusable process composition and semantic material identities; explicit runtime bindings map
 * those identities into Landscape ids at materialization. Specialized callers may still provide a
 * raw resolver. This bootstrap owns neither authored-data parsing nor runtime world state.</p>
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
            throw new IllegalArgumentException(
                    "generated world generators must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.terrainMaterialGenerator = terrainMaterialGenerator;
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

        TerrainMaterializationResult materialization = assembly.materializeGeneratedTerrain(
                atlas.elevation(),
                materials);
        materializeInitialSurfaceWater(atlas, assembly);
        assembly.generatedHydroClimate(atlas.hydroClimate());

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
                int waterZ = Math.addExact(
                        atlas.elevation().elevationAt(worldX, worldY),
                        1);
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
