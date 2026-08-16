package io.github.evoforge.simulation.world.terrain.generation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.atlas.DrainageField;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class TerrainShorelineMaterialGenerationTest {
    private static final TerrainMaterialKey TOPSOIL = TerrainMaterialKey.of("test:topsoil");
    private static final TerrainMaterialKey SOIL = TerrainMaterialKey.of("test:soil");
    private static final TerrainMaterialKey SAND = TerrainMaterialKey.of("test:sand");
    private static final TerrainMaterialKey ROCK = TerrainMaterialKey.of("test:rock");

    @Test
    void depositionalPresetTurnsDerivedShorelineSurfaceIntoSediment() {
        WorldBounds bounds = new WorldBounds(0, 2, 0, 2, -4, 4);
        ElevationField elevation = flatElevation(bounds);
        DrainageField drainage = minimalDrainage(bounds);
        SurfaceHydrologyField hydrology = shorelineAt(bounds, 0, 1);
        CompiledTerrainProfile profile = profile(true);
        TerrainMaterialGenerationStage generator = new TerrainMaterialGenerationStage();

        TerrainMaterialField withoutHydrology = generator.generate(
                elevation,
                drainage,
                profile);
        TerrainMaterialField withHydrology = generator.generate(
                elevation,
                drainage,
                hydrology,
                profile);

        assertEquals(TOPSOIL, withoutHydrology.materialAt(0, 1, 0));
        assertEquals(SAND, withHydrology.materialAt(0, 1, 0));
        assertEquals(TOPSOIL, withHydrology.materialAt(2, 1, 0));
    }

    @Test
    void naturalGroundWithoutDepositionDoesNotAcquireImplicitSedimentRequirement() {
        WorldBounds bounds = new WorldBounds(0, 2, 0, 2, -4, 4);
        CompiledTerrainProfile naturalGround = new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition(
                        "test:natural",
                        List.of(TerrainPresetCatalog.NATURAL_GROUND),
                        "test:ground"),
                new TerrainMaterialSetDefinition(
                        "test:ground",
                        Map.of(
                                TerrainMaterialRole.SURFACE, TOPSOIL,
                                TerrainMaterialRole.SUBSURFACE, SOIL,
                                TerrainMaterialRole.BEDROCK, ROCK)));

        TerrainMaterialField field = new TerrainMaterialGenerationStage().generate(
                flatElevation(bounds),
                minimalDrainage(bounds),
                shorelineAt(bounds, 0, 1),
                naturalGround);

        assertEquals(TOPSOIL, field.materialAt(0, 1, 0));
    }

    private static CompiledTerrainProfile profile(boolean deposition) {
        return new TerrainProfileCompiler().compile(
                new TerrainProfileDefinition(
                        "test:terrain",
                        deposition
                                ? List.of(
                                        TerrainPresetCatalog.NATURAL_GROUND,
                                        TerrainPresetCatalog.DEPOSITIONAL_SAND)
                                : List.of(TerrainPresetCatalog.NATURAL_GROUND),
                        "test:materials"),
                new TerrainMaterialSetDefinition(
                        "test:materials",
                        Map.of(
                                TerrainMaterialRole.SURFACE, TOPSOIL,
                                TerrainMaterialRole.SUBSURFACE, SOIL,
                                TerrainMaterialRole.SEDIMENT, SAND,
                                TerrainMaterialRole.BEDROCK, ROCK)));
    }

    private static ElevationField flatElevation(WorldBounds bounds) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                requireContains(x, y);
                return 0;
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside elevation");
            }
        };
    }

    private static DrainageField minimalDrainage(WorldBounds bounds) {
        return new DrainageField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public boolean hasDownstream(int x, int y) {
                requireContains(x, y);
                return false;
            }

            @Override
            public int downstreamXAt(int x, int y) {
                requireContains(x, y);
                return x;
            }

            @Override
            public int downstreamYAt(int x, int y) {
                requireContains(x, y);
                return y;
            }

            @Override
            public long contributingAreaAt(int x, int y) {
                requireContains(x, y);
                return 1L;
            }

            @Override
            public int terminalXAt(int x, int y) {
                requireContains(x, y);
                return x;
            }

            @Override
            public int terminalYAt(int x, int y) {
                requireContains(x, y);
                return y;
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside drainage");
            }
        };
    }

    private static SurfaceHydrologyField shorelineAt(
            WorldBounds bounds,
            int shoreX,
            int shoreY) {
        return new SurfaceHydrologyField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int initialWaterVolumeAt(int x, int y) {
                requireContains(x, y);
                return 0;
            }

            @Override
            public boolean isShoreline(int x, int y) {
                requireContains(x, y);
                return x == shoreX && y == shoreY;
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside hydrology");
            }
        };
    }
}
