package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.surface.SurfaceMorphologyField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialField;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SoilFormationGenerationStageTest {
    private static final TerrainMaterialKey SOIL = TerrainMaterialKey.of("test:soil");
    private static final TerrainMaterialKey ROCK = TerrainMaterialKey.of("test:rock");
    private static final WorldBounds BOUNDS = new WorldBounds(0, 2, 0, 0, 0, 0);

    @Test
    void sameMaterialDevelopsDifferentProfilesFromGeneratedGeomorphicCauses() {
        SoilFormationGenerationStage stage = exactTestStage();
        SoilSemanticProfile base = new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(700_000));

        SoilHydraulicProfileField field = stage.generate(
                uniformMaterial(SOIL),
                morphology(
                        new long[] {1_000_000L, 0L, 0L},
                        new long[] {0L, 0L, 1_000_000L}),
                drainage(new long[] {1L, 1L, 3L}),
                SoilSemanticProfileBindings.of(Map.of(SOIL, base)));

        assertEquals(400_000, field.find(0, 0, 0).porosityPartsPerMillion());
        assertEquals(500_000, field.find(1, 0, 0).porosityPartsPerMillion());
        assertEquals(650_000, field.find(2, 0, 0).porosityPartsPerMillion());
    }

    @Test
    void absoluteSlopeDoesNotTurnConcaveAccumulationIntoExposure() {
        SoilFormationGenerationStage stage = exactTestStage();
        SoilSemanticProfile base = new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(700_000));
        SurfaceMorphologyField morphology = new SurfaceMorphologyField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public long maximumNeighborSlopeSubunitsAt(int x, int y) {
                return x == 2 ? 4_000_000L : 0L;
            }
            @Override public long convexitySubunitsAt(int x, int y) { return 0L; }
            @Override public long concavitySubunitsAt(int x, int y) {
                return x == 2 ? 1_000_000L : 0L;
            }
        };

        SoilHydraulicProfileField field = stage.generate(
                uniformMaterial(SOIL),
                morphology,
                drainage(new long[] {1L, 1L, 3L}),
                SoilSemanticProfileBindings.of(Map.of(SOIL, base)));

        assertEquals(650_000, field.find(2, 0, 0).porosityPartsPerMillion());
    }

    @Test
    void materialWithoutAuthoredSoilSemanticsRemainsNonSoil() {
        SoilFormationGenerationStage stage = exactTestStage();
        TerrainMaterialField materials = new TerrainMaterialField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public TerrainMaterialKey materialAt(int x, int y, int z) {
                return x == 1 ? ROCK : SOIL;
            }
        };
        SoilSemanticProfile base = new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(700_000));

        SoilHydraulicProfileField field = stage.generate(
                materials,
                morphology(new long[3], new long[3]),
                drainage(new long[] {1L, 1L, 1L}),
                SoilSemanticProfileBindings.of(Map.of(SOIL, base)));

        assertNull(field.find(1, 0, 0));
    }

    @Test
    void rejectsCausalFieldsFromDifferentWorldBounds() {
        SoilFormationGenerationStage stage = exactTestStage();
        WorldBounds other = new WorldBounds(0, 1, 0, 0, 0, 0);
        SurfaceMorphologyField wrongMorphology = new SurfaceMorphologyField() {
            @Override public WorldBounds bounds() { return other; }
            @Override public long maximumNeighborSlopeSubunitsAt(int x, int y) { return 0L; }
            @Override public long convexitySubunitsAt(int x, int y) { return 0L; }
            @Override public long concavitySubunitsAt(int x, int y) { return 0L; }
        };

        assertThrows(
                IllegalArgumentException.class,
                () -> stage.generate(
                        uniformMaterial(SOIL),
                        wrongMorphology,
                        drainage(new long[] {1L, 1L, 1L}),
                        SoilSemanticProfileBindings.of(Map.of())));
    }

    private static SoilFormationGenerationStage exactTestStage() {
        SoilCompositionCompiler composition = semantic -> {
            if (semantic.organicMatter().partsPerMillion() != 700_000) {
                throw new AssertionError("geomorphic formation changed authored organic character");
            }
            int fine = semantic.mineralFineness().partsPerMillion();
            return new SoilCompositionProfile(
                    NormalizedValue.SCALE - fine,
                    0,
                    fine,
                    semantic.organicMatter().partsPerMillion());
        };
        SoilHydraulicCalibrator hydraulics = profile -> new SoilHydraulicProfile(
                profile.clayPartsPerMillion(),
                0,
                0,
                WaterDepthRate.ZERO);
        return new SoilFormationGenerationStage(
                new SoilFormationCalibration(
                        1_000_000L,
                        1_000_000L,
                        NormalizedValue.ofPartsPerMillion(200_000)),
                composition,
                hydraulics);
    }

    private static TerrainMaterialField uniformMaterial(TerrainMaterialKey material) {
        return new TerrainMaterialField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public TerrainMaterialKey materialAt(int x, int y, int z) { return material; }
        };
    }

    private static SurfaceMorphologyField morphology(long[] convexity, long[] concavity) {
        return new SurfaceMorphologyField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public long maximumNeighborSlopeSubunitsAt(int x, int y) {
                return Math.max(convexity[x], concavity[x]);
            }
            @Override public long convexitySubunitsAt(int x, int y) { return convexity[x]; }
            @Override public long concavitySubunitsAt(int x, int y) { return concavity[x]; }
        };
    }

    private static DrainageField drainage(long[] contributingArea) {
        return new DrainageField() {
            @Override public WorldBounds bounds() { return BOUNDS; }
            @Override public boolean hasDownstream(int x, int y) { return false; }
            @Override public int downstreamXAt(int x, int y) { return x; }
            @Override public int downstreamYAt(int x, int y) { return y; }
            @Override public long contributingAreaAt(int x, int y) { return contributingArea[x]; }
            @Override public int terminalXAt(int x, int y) { return x; }
            @Override public int terminalYAt(int x, int y) { return y; }
        };
    }
}
