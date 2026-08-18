package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerator;
import io.github.evoforge.simulation.world.calibration.soil.SoilHydraulicProfileField;
import io.github.evoforge.simulation.world.calibration.soil.SoilSemanticProfileBindings;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.terrain.surface.SurfaceMorphologyGenerator;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerator;

/**
 * Pure generated-world preparation phase.
 *
 * <p>This phase turns immutable genesis provenance and authored semantic archetypes into immutable
 * world facts, stable generated material identities, surface geometry and optional generated
 * physical landscape properties. It never creates or starts a SimulationRuntime and never mutates
 * runtime Water, Soil, WeatherState, schedulers, or object stores.</p>
 *
 * <p>Algorithm wiring is owned by {@link WorldPreparationAlgorithms}. This class orchestrates the
 * dependency order only; individual preparation algorithms remain independently replaceable.</p>
 */
public final class GeneratedWorldPreparation {
    private final WorldAtlasGenerator atlasGenerator;
    private final WorldPreparationAlgorithms algorithms;

    public GeneratedWorldPreparation() {
        this(new WorldAtlasGenerator(), WorldPreparationAlgorithms.standard());
    }

    public GeneratedWorldPreparation(WorldAtlasGenerator atlasGenerator) {
        this(atlasGenerator, WorldPreparationAlgorithms.standard());
    }

    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        this(
                atlasGenerator,
                WorldPreparationAlgorithms.standard().withTerrainMaterial(terrainMaterialGenerator));
    }

    /** Compatibility injection seam retaining the standard surface-geometry compiler. */
    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            SurfaceMorphologyGenerator surfaceMorphologyGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            SoilFormationGenerator soilFormationGenerator) {
        this(
                atlasGenerator,
                new WorldPreparationAlgorithms(
                        surfaceMorphologyGenerator,
                        TerrainShapeGenerationStage.standard(),
                        terrainMaterialGenerator,
                        soilFormationGenerator));
    }

    /** Compatibility seam for callers that already inject each preparation algorithm separately. */
    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            SurfaceMorphologyGenerator surfaceMorphologyGenerator,
            TerrainShapeGenerator terrainShapeGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            SoilFormationGenerator soilFormationGenerator) {
        this(
                atlasGenerator,
                new WorldPreparationAlgorithms(
                        surfaceMorphologyGenerator,
                        terrainShapeGenerator,
                        terrainMaterialGenerator,
                        soilFormationGenerator));
    }

    /** Canonical composition seam for generated-world preparation. */
    public GeneratedWorldPreparation(
            WorldAtlasGenerator atlasGenerator,
            WorldPreparationAlgorithms algorithms) {
        if (atlasGenerator == null || algorithms == null) {
            throw new IllegalArgumentException("world preparation dependencies must not be null");
        }
        this.atlasGenerator = atlasGenerator;
        this.algorithms = algorithms;
    }

    /** Generates durable facts only, for specialized materialization paths. */
    public WorldAtlas generateFacts(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("world genesis must not be null");
        }
        return atlasGenerator.generate(genesis);
    }

    /** Generates durable facts plus stable terrain material identities and surface geometry. */
    public PreparedGeneratedWorld prepare(
            WorldGenesis genesis,
            CompiledTerrainProfile profile) {
        PreparedTerrain prepared = prepareTerrain(genesis, profile);
        return new PreparedGeneratedWorld(
                prepared.atlas(),
                prepared.materials(),
                prepared.shapes(),
                GeneratedLandscapeProperties.empty(prepared.atlas().genesis().spec().bounds()));
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
        SoilHydraulicProfileField soilHydraulics = algorithms.soilFormation().generate(
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
                prepared.shapes(),
                new GeneratedLandscapeProperties(soilHydraulics));
    }

    private PreparedTerrain prepareTerrain(
            WorldGenesis genesis,
            CompiledTerrainProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("compiled terrain profile must not be null");
        }
        WorldAtlas atlas = generateFacts(genesis);
        SurfaceMorphologyField morphology = algorithms.surfaceMorphology().generate(atlas.elevation());
        if (morphology == null) {
            throw new IllegalStateException("surface morphology generator returned null");
        }
        if (!atlas.genesis().spec().bounds().equals(morphology.bounds())) {
            throw new IllegalStateException(
                    "generated surface morphology must match world genesis bounds");
        }
        TerrainShapeField shapes = algorithms.terrainShape().generate(
                atlas.genesis().generationRevision(),
                atlas.elevation());
        if (shapes == null) {
            throw new IllegalStateException("terrain shape generator returned null");
        }
        if (!atlas.genesis().spec().bounds().equals(shapes.bounds())) {
            throw new IllegalStateException("generated terrain shapes must match world genesis bounds");
        }
        TerrainMaterialField materials = algorithms.terrainMaterial().generate(
                atlas.elevation(),
                atlas.geology(),
                atlas.drainage(),
                atlas.surfaceHydrology(),
                morphology,
                profile);
        if (materials == null) {
            throw new IllegalStateException("terrain material generator returned null");
        }
        return new PreparedTerrain(atlas, morphology, materials, shapes);
    }

    private record PreparedTerrain(
            WorldAtlas atlas,
            SurfaceMorphologyField morphology,
            TerrainMaterialField materials,
            TerrainShapeField shapes) { }
}
