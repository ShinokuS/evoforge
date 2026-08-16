package io.github.evoforge.simulation.world.terrain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.DrainageGenerationStage;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class TerrainMaterialGenerationStageTest {

    private static final TerrainMaterialKey TOPSOIL = TerrainMaterialKey.of("test:topsoil");
    private static final TerrainMaterialKey SOIL = TerrainMaterialKey.of("test:soil");
    private static final TerrainMaterialKey SAND = TerrainMaterialKey.of("test:sand");
    private static final TerrainMaterialKey ROCK = TerrainMaterialKey.of("test:rock");

    @Test
    void flatStableGroundBuildsTopsoilSoilThenRock() {
        TestElevationField elevation = constantElevation(0L, -5, 5);
        TerrainMaterialField field = generate(elevation, naturalGroundPalette());

        assertEquals(TOPSOIL, field.materialAt(1, 1, 0));
        assertEquals(SOIL, field.materialAt(1, 1, -1));
        assertEquals(SOIL, field.materialAt(1, 1, -2));
        assertEquals(SOIL, field.materialAt(1, 1, -3));
        assertEquals(ROCK, field.materialAt(1, 1, -4));
    }

    @Test
    void steepSurfaceExposesBedrockInsteadOfForcingSoil() {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        TestElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -5, 5),
                new long[] {
                        0, 0, 0,
                        0, 2 * cell, 0,
                        0, 0, 0
                });
        TerrainMaterialField materials = generate(
                elevation,
                naturalGroundPalette());

        assertEquals(ROCK, materials.materialAt(1, 1, 2));
    }

    @Test
    void lowSlopeDrainageDepressionAccumulatesSandWithoutAbsoluteHeightRule() {
        TestElevationField elevation = shallowBowl(0, -5, 5);
        TerrainMaterialField materials = generate(
                elevation,
                naturalGroundAndDepositionPalette());

        assertEquals(SAND, materials.materialAt(1, 1, -1));
        assertEquals(SAND, materials.materialAt(1, 1, -2));
        assertEquals(SOIL, materials.materialAt(1, 1, -3));
    }

    @Test
    void verticalTranslationPreservesRelativeMaterialPattern() {
        TestElevationField base = shallowBowl(0, -5, 5);
        TestElevationField shifted = shallowBowl(7, 2, 12);
        TerrainPalette palette = naturalGroundAndDepositionPalette();
        TerrainMaterialField first = generate(base, palette);
        TerrainMaterialField second = generate(shifted, palette);

        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 2; x++) {
                int firstSurface = base.elevationAt(x, y);
                int secondSurface = shifted.elevationAt(x, y);
                int maxDepth = firstSurface - base.bounds().minZ();
                for (int depth = 0; depth <= maxDepth; depth++) {
                    assertEquals(
                            first.materialAt(x, y, firstSurface - depth),
                            second.materialAt(x, y, secondSurface - depth),
                            "material pattern changed after vertical translation at "
                                    + x + "," + y + " depth=" + depth);
                }
            }
        }
    }

    private static TerrainMaterialField generate(
            TestElevationField elevation,
            TerrainPalette palette) {
        DrainageField drainage = new DrainageGenerationStage().generate(elevation);
        return new TerrainMaterialGenerationStage().generate(
                elevation,
                drainage,
                palette);
    }

    private static TerrainPalette naturalGroundPalette() {
        return palette(List.of(new TerrainPreset(
                TerrainPresetCatalog.NATURAL_GROUND,
                TerrainPresetCapability.GROUND_PROFILE)));
    }

    private static TerrainPalette naturalGroundAndDepositionPalette() {
        return palette(List.of(
                new TerrainPreset(
                        TerrainPresetCatalog.NATURAL_GROUND,
                        TerrainPresetCapability.GROUND_PROFILE),
                new TerrainPreset(
                        TerrainPresetCatalog.DEPOSITIONAL_SAND,
                        TerrainPresetCapability.SURFACE_DEPOSITION)));
    }

    private static TerrainPalette palette(List<TerrainPreset> presets) {
        return new TerrainPalette(
                "test:terrain",
                presets,
                new TerrainPaletteMaterials(TOPSOIL, SOIL, SAND, ROCK));
    }

    private static TestElevationField constantElevation(
            long subunits,
            int minZ,
            int maxZ) {
        long[] elevations = new long[9];
        java.util.Arrays.fill(elevations, subunits);
        return field(
                new WorldBounds(0, 2, 0, 2, minZ, maxZ),
                elevations);
    }

    private static TestElevationField shallowBowl(
            int verticalOffsetCells,
            int minZ,
            int maxZ) {
        long offset = Math.multiplyExact(
                (long) verticalOffsetCells,
                ElevationField.SUBUNITS_PER_CELL);
        long center = offset - 200_000L;
        return field(
                new WorldBounds(0, 2, 0, 2, minZ, maxZ),
                new long[] {
                        offset, offset, offset,
                        offset, center, offset,
                        offset, offset, offset
                });
    }

    private static TestElevationField field(
            WorldBounds bounds,
            long[] elevations) {
        return new TestElevationField(bounds, elevations);
    }

    private static final class TestElevationField implements ElevationField {
        private final WorldBounds bounds;
        private final int width;
        private final long[] elevations;

        private TestElevationField(
                WorldBounds bounds,
                long[] elevations) {
            this.bounds = bounds;
            this.width = bounds.maxX() - bounds.minX() + 1;
            this.elevations = elevations.clone();
        }

        @Override
        public WorldBounds bounds() {
            return bounds;
        }

        @Override
        public int elevationAt(int x, int y) {
            return Math.toIntExact(Math.floorDiv(
                    elevationSubunitsAt(x, y),
                    SUBUNITS_PER_CELL));
        }

        @Override
        public long elevationSubunitsAt(int x, int y) {
            if (!contains(x, y)) {
                throw new IllegalArgumentException("outside test elevation field");
            }
            return elevations[(y - bounds.minY()) * width + (x - bounds.minX())];
        }
    }
}
