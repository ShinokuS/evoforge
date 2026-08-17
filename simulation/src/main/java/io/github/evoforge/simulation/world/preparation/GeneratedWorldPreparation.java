package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;

/**
 * Pure generated-world preparation phase.
 *
 * <p>This phase turns immutable genesis provenance into immutable world facts and stable generated
 * material identities. It never creates or starts a SimulationRuntime and never mutates runtime
 * Water, Soil, WeatherState, schedulers, or object stores.</p>
 */
public final class GeneratedWorldPreparation {
    private final WorldAtlasGenerator atlasGenerator;
    private final TerrainMaterialGenerator terrainMaterialGenerator;

    public GeneratedWorldPreparation() {
        this(new WorldAtlasGenerator(), new TerrainMaterialGenerationStage());
    }

    public GeneratedWorldPreparation(WorldAtlasGenerator atlasGenerator) {
        this(atlasGenerator, new TerrainMaterialGenerationStage());
    }

    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        if (atlasGenerator == null || terrainMaterialGenerator == null) {
            throw new IllegalArgumentException("world preparation algorithms must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.terrainMaterialGenerator = terrainMaterialGenerator;
    }

    /** Generates durable facts only, for specialized materialization paths. */
    public WorldAtlas generateFacts(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("world genesis must not be null");
        }
        return atlasGenerator.generate(genesis);
    }

    /** Generates durable facts plus stable terrain material identities. */
    public PreparedGeneratedWorld prepare(
            WorldGenesis genesis,
            CompiledTerrainProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("compiled terrain profile must not be null");
        }
        WorldAtlas atlas = generateFacts(genesis);
        TerrainMaterialField materials = terrainMaterialGenerator.generate(
                atlas.elevation(),
                atlas.geology(),
                atlas.drainage(),
                atlas.surfaceHydrology(),
                profile);
        return new PreparedGeneratedWorld(atlas, materials);
    }
}
