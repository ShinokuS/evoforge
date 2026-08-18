package io.github.evoforge.simulation.world.preparation;

import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerationStage;
import io.github.evoforge.simulation.world.calibration.soil.SoilFormationGenerator;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerationStage;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyGenerator;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerationStage;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeGenerator;

/**
 * Composition bundle for generated-world preparation algorithms.
 *
 * <p>This record is wiring only. It owns no generation policy and allows each preparation algorithm
 * to be replaced independently without expanding {@link GeneratedWorldPreparation} constructors as
 * new real stages appear.</p>
 */
public record WorldPreparationAlgorithms(
        SurfaceMorphologyGenerator surfaceMorphology,
        TerrainShapeGenerator terrainShape,
        TerrainMaterialGenerator terrainMaterial,
        SoilFormationGenerator soilFormation) {

    public WorldPreparationAlgorithms {
        if (surfaceMorphology == null || terrainShape == null
                || terrainMaterial == null || soilFormation == null) {
            throw new IllegalArgumentException("world preparation algorithms must not be null");
        }
    }

    public static WorldPreparationAlgorithms standard() {
        return new WorldPreparationAlgorithms(
                new SurfaceMorphologyGenerationStage(),
                TerrainShapeGenerationStage.standard(),
                new TerrainMaterialGenerationStage(),
                SoilFormationGenerationStage.standard());
    }

    public WorldPreparationAlgorithms withSurfaceMorphology(SurfaceMorphologyGenerator replacement) {
        return new WorldPreparationAlgorithms(
                replacement,
                terrainShape,
                terrainMaterial,
                soilFormation);
    }

    public WorldPreparationAlgorithms withTerrainShape(TerrainShapeGenerator replacement) {
        return new WorldPreparationAlgorithms(
                surfaceMorphology,
                replacement,
                terrainMaterial,
                soilFormation);
    }

    public WorldPreparationAlgorithms withTerrainMaterial(TerrainMaterialGenerator replacement) {
        return new WorldPreparationAlgorithms(
                surfaceMorphology,
                terrainShape,
                replacement,
                soilFormation);
    }

    public WorldPreparationAlgorithms withSoilFormation(SoilFormationGenerator replacement) {
        return new WorldPreparationAlgorithms(
                surfaceMorphology,
                terrainShape,
                terrainMaterial,
                replacement);
    }
}
