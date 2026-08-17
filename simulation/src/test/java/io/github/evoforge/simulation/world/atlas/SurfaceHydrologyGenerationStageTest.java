package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class SurfaceHydrologyGenerationStageTest {

    @Test
    void currentV5PreservesFiniteChannelWaterAndAdjacentDryShoreline() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 17L);
        ElevationField elevation = constantElevation(bounds, 0);
        DrainageField drainage = syntheticDrainage(bounds);

        SurfaceHydrologyField field = new SurfaceHydrologyGenerationStage().generate(
                genesis,
                elevation,
                drainage);

        assertEquals(GenerationRevision.V5, genesis.generationRevision());
        for (int y = 0; y <= 4; y++) {
            assertTrue(field.isInitiallyWet(2, y));
            assertFalse(field.isShoreline(2, y));
        }
        assertTrue(field.isShoreline(1, 2));
        assertTrue(field.isShoreline(3, 2));
        assertFalse(field.isShoreline(0, 2));
        assertFalse(field.isShoreline(4, 2));
        assertTrue(field.initialWaterVolumeAt(2, 4) > field.initialWaterVolumeAt(2, 0));
        assertTrue(field.initialWaterVolumeAt(2, 0) > 0);
        assertTrue(field.initialWaterVolumeAt(2, 4) < 1_000_000);
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
