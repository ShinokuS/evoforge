package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.materialization.TerrainMaterializationResult;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * One-shot production composition path from immutable genesis provenance into a started runtime.
 *
 * <p>Content composition stays explicit: callers prepare the {@link SimulationAssembly} with the
 * definitions/mechanics their content pack supplies, and provide the generated terrain material
 * resolver. This bootstrap owns neither content selection nor runtime world state.</p>
 */
public final class GeneratedWorldBootstrap {

    private final WorldAtlasGenerator atlasGenerator;

    public GeneratedWorldBootstrap() {
        this(new WorldAtlasGenerator());
    }

    public GeneratedWorldBootstrap(WorldAtlasGenerator atlasGenerator) {
        if (atlasGenerator == null) {
            throw new IllegalArgumentException("atlasGenerator must not be null");
        }
        this.atlasGenerator = atlasGenerator;
    }

    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        if (genesis == null || assembly == null || materials == null) {
            throw new IllegalArgumentException(
                    "generated world bootstrap dependencies must not be null");
        }

        WorldAtlas atlas = atlasGenerator.generate(genesis);
        WorldBounds bounds = genesis.spec().bounds();
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
