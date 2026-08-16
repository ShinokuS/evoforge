package io.github.evoforge.simulation.world.terrain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.DrainageGenerationStage;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TerrainMaterialGenerationStageTest {
    private static final TerrainMaterialKey TOPSOIL = TerrainMaterialKey.of("test:topsoil");
    private static final TerrainMaterialKey SOIL = TerrainMaterialKey.of("test:soil");
    private static final TerrainMaterialKey SAND = TerrainMaterialKey.of("test:sand");
    private static final TerrainMaterialKey ROCK = TerrainMaterialKey.of("test:rock");

    @Test
    void flatStableGroundBuildsSurfaceSubsurfaceThenBedrock() {
        TestElevationField elevation = constantElevation(0L, -5, 5);
        TerrainMaterialField field = generate(elevation, naturalGroundProfile(materials()));

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
        TerrainMaterialField result = generate(elevation, naturalGroundProfile(materials()));

        assertEquals(ROCK, result.materialAt(1, 1, 2));
    }

    @Test
    void lowSlopeDrainageDepressionAccumulatesSedimentWithoutAbsoluteHeightRule() {
        TestElevationField elevation = shallowBowl(0, -5, 5);
        TerrainMaterialField result = generate(elevation, fullProfile(materials()));

        assertEquals(SAND, result.materialAt(1, 1, -1));
        assertEquals(SAND, result.materialAt(1, 1, -2));
        assertEquals(SOIL, result.materialAt(1, 1, -3));
    }

    @Test
    void verticalTranslationPreservesRelativeMaterialPattern() {
        TestElevationField base = shallowBowl(0, -5, 5);
        TestElevationField shifted = shallowBowl(7, 2, 12);
        CompiledTerrainProfile profile = fullProfile(materials());
        TerrainMaterialField first = generate(base, profile);
        TerrainMaterialField second = generate(shifted, profile);

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

    @Test
    void changingMaterialSetPreservesStructureAndOnlyChangesMaterialIdentities() {
        TestElevationField elevation = shallowBowl(0, -5, 5);
        Map<TerrainMaterialRole, TerrainMaterialKey> firstMaterials = materials();
        Map<TerrainMaterialRole, TerrainMaterialKey> secondMaterials = Map.of(
                TerrainMaterialRole.SURFACE, TerrainMaterialKey.of("test:volcanic_surface"),
                TerrainMaterialRole.SUBSURFACE, TerrainMaterialKey.of("test:ash_subsurface"),
                TerrainMaterialRole.SEDIMENT, TerrainMaterialKey.of("test:black_sediment"),
                TerrainMaterialRole.BEDROCK, TerrainMaterialKey.of("test:basalt"));
        TerrainMaterialField first = generate(elevation, fullProfile(firstMaterials));
        TerrainMaterialField second = generate(elevation, fullProfile(secondMaterials));

        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 2; x++) {
                int surface = elevation.elevationAt(x, y);
                for (int z = elevation.bounds().minZ(); z <= surface; z++) {
                    TerrainMaterialRole role = roleOf(firstMaterials, first.materialAt(x, y, z));
                    assertEquals(secondMaterials.get(role), second.materialAt(x, y, z));
                }
            }
        }
    }

    private static TerrainMaterialField generate(
            TestElevationField elevation,
            CompiledTerrainProfile profile) {
        DrainageField drainage = new DrainageGenerationStage().generate(elevation);
        return new TerrainMaterialGenerationStage().generate(elevation, drainage, profile);
    }

    private static CompiledTerrainProfile naturalGroundProfile(
            Map<TerrainMaterialRole, TerrainMaterialKey> materials) {
        return compile(
                List.of(TerrainPresetCatalog.NATURAL_GROUND),
                materials);
    }

    private static CompiledTerrainProfile fullProfile(
            Map<TerrainMaterialRole, TerrainMaterialKey> materials) {
        return compile(
                List.of(
                        TerrainPresetCatalog.NATURAL_GROUND,
                        TerrainPresetCatalog.DEPOSITIONAL_SAND),
                materials);
    }

    private static CompiledTerrainProfile compile(
            List<String> presetKeys,
            Map<TerrainMaterialRole, TerrainMaterialKey> materials) {
        return new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition("test:terrain", presetKeys, "test:materials"),
                new TerrainMaterialSetDefinition("test:materials", materials));
    }

    private static Map<TerrainMaterialRole, TerrainMaterialKey> materials() {
        return Map.of(
                TerrainMaterialRole.SURFACE, TOPSOIL,
                TerrainMaterialRole.SUBSURFACE, SOIL,
                TerrainMaterialRole.SEDIMENT, SAND,
                TerrainMaterialRole.BEDROCK, ROCK);
    }

    private static TerrainMaterialRole roleOf(
            Map<TerrainMaterialRole, TerrainMaterialKey> materials,
            TerrainMaterialKey key) {
        for (Map.Entry<TerrainMaterialRole, TerrainMaterialKey> entry : materials.entrySet()) {
            if (entry.getValue().equals(key)) return entry.getKey();
        }
        throw new AssertionError("unknown test material: " + key);
    }

    private static TestElevationField constantElevation(long subunits, int minZ, int maxZ) {
        long[] elevations = new long[9];
        java.util.Arrays.fill(elevations, subunits);
        return field(new WorldBounds(0, 2, 0, 2, minZ, maxZ), elevations);
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

    private static TestElevationField field(WorldBounds bounds, long[] elevations) {
        return new TestElevationField(bounds, elevations);
    }

    private static final class TestElevationField implements ElevationField {
        private final WorldBounds bounds;
        private final int width;
        private final long[] elevations;

        private TestElevationField(WorldBounds bounds, long[] elevations) {
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
