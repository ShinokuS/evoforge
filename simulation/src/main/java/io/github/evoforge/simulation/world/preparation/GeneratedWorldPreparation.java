package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerationStage;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfileBindings;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerationStage;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerator;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;

/**
 * Pure generated-world preparation phase.
 *
 * <p>This phase turns immutable genesis provenance and authored semantic archetypes into immutable
 * world facts, stable generated material identities and optional generated physical landscape
 * properties. It never creates or starts a SimulationRuntime and never mutates runtime Water, Soil,
 * WeatherState, schedulers, or object stores.</p>
 */
public final class GeneratedWorldPreparation {
    private final WorldAtlasGenerator atlasGenerator;
    private final SurfaceMorphologyGenerator surfaceMorphologyGenerator;
    private final TerrainMaterialGenerator terrainMaterialGenerator;
    private final SoilFormationGenerator soilFormationGenerator;

    public GeneratedWorldPreparation() {
        this(
                new WorldAtlasGenerator(),
                new SurfaceMorphologyGenerationStage(),
                new TerrainMaterialGenerationStage(),
                SoilFormationGenerationStage.standard());
    }

    public GeneratedWorldPreparation(WorldAtlasGenerator atlasGenerator) {
        this(
                atlasGenerator,
                new SurfaceMorphologyGenerationStage(),
                new TerrainMaterialGenerationStage(),
                SoilFormationGenerationStage.standard());
    }

    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        this(
                atlasGenerator,
                new SurfaceMorphologyGenerationStage(),
                terrainMaterialGenerator,
                SoilFormationGenerationStage.standard());
    }

    /** Canonical injection seam for replaceable landscape-preparation algorithms. */
    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            SurfaceMorphologyGenerator surfaceMorphologyGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            SoilFormationGenerator soilFormationGenerator) {
        if (atlasGenerator == null
                || surfaceMorphologyGenerator == null
                || terrainMaterialGenerator == null
                || soilFormationGenerator == null) {
            throw new IllegalArgumentException("world preparation algorithms must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.surfaceMorphologyGenerator = surfaceMorphologyGenerator;
        this.terrainMaterialGenerator = terrainMaterialGenerator;
        this.soilFormationGenerator = soilFormationGenerator;
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
        PreparedTerrain prepared = prepareTerrain(genesis, profile);
        return new PreparedGeneratedWorld(prepared.atlas(), prepared.materials());
    }

    /**
     * Generates terrain identities plus authoritative spatial physical Soil properties.
     *
     * <p>Definition loading still stops at immutable semantic archetypes. This preparation step
     * develops those archetypes from generated morphology/drainage and calibrates physical
     * hydraulics before any runtime exists.</p>
     */
    public PreparedGeneratedWorld prepare(
            WorldGenesis genesis,
            CompiledTerrainProfile profile,
            SoilSemanticProfileBindings soilSemantics) {
        if (soilSemantics == null) {
            throw new IllegalArgumentException("soil semantic bindings must not be null");
        }
        PreparedTerrain prepared = prepareTerrain(genesis, profile);
        SoilHydraulicProfileField soilHydraulics = soilFormationGenerator.generate(
                prepared.materials(),
                prepared.morphology(),
                prepared.atlas().drainage(),
                soilSemantics);
        if (soilHydraulics == null) {
            throw new IllegalStateException("soil formation generator returned null");
        }
        return new PreparedGeneratedWorld(
                prepared.atlas(),
                prepared.materials(),
                new GeneratedLandscapeProperties(soilHydraulics));
    }

    private PreparedTerrain prepareTerrain(
            WorldGenesis genesis,
            CompiledTerrainProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("compiled terrain profile must not be null");
        }
        WorldAtlas atlas = generateFacts(genesis);
        SurfaceMorphologyField morphology = surfaceMorphologyGenerator.generate(atlas.elevation());
        if (morphology == null) {
            throw new IllegalStateException("surface morphology generator returned null");
        }
        if (!atlas.genesis().spec().bounds().equals(morphology.bounds())) {
            throw new IllegalStateException(
                    "generated surface morphology must match world genesis bounds");
        }
        TerrainMaterialField materials = terrainMaterialGenerator.generate(
                atlas.elevation(),
                atlas.geology(),
                atlas.drainage(),
                atlas.surfaceHydrology(),
                morphology,
                profile);
        if (materials == null) {
            throw new IllegalStateException("terrain material generator returned null");
        }
        return new PreparedTerrain(atlas, morphology, materials);
    }

    private record PreparedTerrain(
            WorldAtlas atlas,
            SurfaceMorphologyField morphology,
            TerrainMaterialField materials) { }
}
