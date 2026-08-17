package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class SurfaceHydrologyGenerationStageTest {

    @Test
    void v7ScalesFiniteInitialChannelWaterByClimateMoistureShare() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        ElevationField elevation = constantElevation(bounds, 0);
        DrainageField drainage = syntheticDrainage(bounds);
        SurfaceHydrologyGenerationStage stage = new SurfaceHydrologyGenerationStage();

        SurfaceHydrologyField legacyV6 = stage.generate(
                genesis(bounds, climate(0L, 1L), GenerationRevision.V6),
                elevation,
                drainage);
        SurfaceHydrologyField balancedV7 = stage.generate(
                genesis(bounds, climate(1L, 1L), GenerationRevision.V7),
                elevation,
                drainage);
        SurfaceHydrologyField humidV7 = stage.generate(
                genesis(bounds, climate(3L, 1L), GenerationRevision.V7),
                elevation,
                drainage);
        SurfaceHydrologyField dryV7 = stage.generate(
                genesis(bounds, climate(0L, 1L), GenerationRevision.V7),
                elevation,
                drainage);

        for (int y = 0; y <= 4; y++) {
            int legacy = legacyV6.initialWaterVolumeAt(2, y);
            assertTrue(legacy > 0);
            assertEquals(legacy / 2, balancedV7.initialWaterVolumeAt(2, y));
            assertEquals((legacy * 3) / 4, humidV7.initialWaterVolumeAt(2, y));
            assertEquals(0, dryV7.initialWaterVolumeAt(2, y));
        }

        assertTrue(balancedV7.isShoreline(1, 2));
        assertTrue(balancedV7.isShoreline(3, 2));
        assertFalse(balancedV7.isShoreline(0, 2));
        assertFalse(balancedV7.isShoreline(4, 2));
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                assertFalse(dryV7.isInitiallyWet(x, y));
                assertFalse(dryV7.isShoreline(x, y));
            }
        }
    }

    @Test
    void currentV7UsesNeutralBaselineAndKeepsDrainageOrdering() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 17L);
        SurfaceHydrologyField field = new SurfaceHydrologyGenerationStage().generate(
                genesis,
                constantElevation(bounds, 0),
                syntheticDrainage(bounds));

        assertEquals(GenerationRevision.V7, genesis.generationRevision());
        assertTrue(field.initialWaterVolumeAt(2, 4) > field.initialWaterVolumeAt(2, 0));
        assertEquals(150_000, field.initialWaterVolumeAt(2, 0));
        assertEquals(350_000, field.initialWaterVolumeAt(2, 4));
    }

    @Test
    void preV3RevisionsPreserveAbsenceOfGeneratedSurfaceWater() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                17L,
                GenerationRevision.V2,
                RngRevision.V1);

        SurfaceHydrologyField field = new SurfaceHydrologyGenerationStage().generate(
                genesis,
                constantElevation(bounds, 0),
                syntheticDrainage(bounds));

        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                assertEquals(0, field.initialWaterVolumeAt(x, y));
                assertFalse(field.isShoreline(x, y));
            }
        }
    }

    private static WorldGenesis genesis(
            WorldBounds bounds,
            ClimateSpec climate,
            GenerationRevision revision) {
        return new WorldGenesis(
                new WorldSpec(bounds, climate),
                17L,
                revision,
                RngRevision.V1);
    }

    private static ClimateSpec climate(long precipitation, long evaporation) {
        return ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                CellVolumeRate.of(precipitation, 1L),
                CellVolumeRate.of(evaporation, 1L));
    }

    private static ElevationField constantElevation(WorldBounds bounds, int value) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                requireContains(x, y);
                return value;
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test elevation");
            }
        };
    }

    private static DrainageField syntheticDrainage(WorldBounds bounds) {
        long[] channel = {5L, 6L, 8L, 12L, 25L};
        return new DrainageField() {
            @Override
            public WorldBounds bounds() { return bounds; }

            @Override
            public boolean hasDownstream(int x, int y) {
                requireContains(x, y);
                return y < bounds.maxY();
            }

            @Override
            public int downstreamXAt(int x, int y) {
                requireContains(x, y);
                return x;
            }

            @Override
            public int downstreamYAt(int x, int y) {
                requireContains(x, y);
                return Math.min(bounds.maxY(), y + 1);
            }

            @Override
            public long contributingAreaAt(int x, int y) {
                requireContains(x, y);
                return x == 2 ? channel[y] : 1L;
            }

            @Override
            public int terminalXAt(int x, int y) {
                requireContains(x, y);
                return x;
            }

            @Override
            public int terminalYAt(int x, int y) {
                requireContains(x, y);
                return bounds.maxY();
            }

            private void requireContains(int x, int y) {
                if (!contains(x, y)) throw new IllegalArgumentException("outside test drainage");
            }
        };
    }
}
