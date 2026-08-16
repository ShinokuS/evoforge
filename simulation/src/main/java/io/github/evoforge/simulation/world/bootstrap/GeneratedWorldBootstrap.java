package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import io.github.evoforge.simulation.world.terrain.generation.TerrainPalette;

/**
 * One-shot production composition path from immutable genesis provenance into a started runtime.
 *
 * <p>Content composition stays explicit: callers prepare the {@link SimulationAssembly} with the
 * definitions/mechanics their content pack supplies. A palette path provides semantic generated
 * material keys plus explicit runtime bindings; specialized callers may still provide a raw
 * resolver. This bootstrap owns neither content selection nor runtime world state.</p>
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

    /**
     * Production palette path: causal Atlas facts derive semantic material strata, then explicit
     * content bindings resolve those keys into runtime Landscape ids at materialization.
     */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainPalette palette,
            TerrainMaterialBindings bindings) {
        if (genesis == null
                || assembly == null
                || palette == null
                || bindings == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }

        WorldAtlas atlas = atlasGenerator.generate(genesis);
        TerrainMaterialField materials = terrainMaterialGenerator.generate(
                atlas.elevation(),
                atlas.drainage(),
                palette);
        return start(
                atlas,
                assembly,
                TerrainMaterialResolver.resolved(materials, bindings));
    }

    /** Compatibility/custom path for callers that intentionally own material resolution. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        if (genesis == null || assembly == null || materials == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }

        return start(
                atlasGenerator.generate(genesis),
                assembly,
                materials);
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

        TerrainMaterializationResult materialization =
                assembly.materializeGeneratedTerrain(
                        atlas.elevation(),
                        materials);
        assembly.generatedHydroClimate(atlas.hydroClimate());

        SimulationRuntime runtime = assembly.start();
        return new GeneratedWorldRuntime(
                atlas,
                materialization,
                runtime);
    }
}
