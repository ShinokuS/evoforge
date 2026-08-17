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

final class HydrographyGenerationStageTest {

    @Test
    void v6OwnsChannelFootprintThatInitialSurfaceWaterConsumes() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 17L);
        ElevationField elevation = constantElevation(bounds, 0);
        DrainageField drainage = syntheticDrainage(bounds);

        HydrographyField hydrography = new HydrographyGenerationStage().generate(
                genesis,
                elevation,
                drainage);
        SurfaceHydrologyField initialWater = new SurfaceHydrologyGenerationStage().generate(
                genesis,
                elevation,
                drainage,
                hydrography);

        assertEquals(GenerationRevision.V6, genesis.generationRevision());
        for (int y = 0; y <= 4; y++) {
            assertTrue(hydrography.isChannelAt(2, y));
            assertTrue(initialWater.isInitiallyWet(2, y));
            assertFalse(hydrography.isChannelAt(1, y));
        }
        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                assertEquals(
                        hydrography.isChannelAt(x, y),
                        initialWater.isInitiallyWet(x, y),
                        "v6 legacy initial-Water footprint drifted from generated channels");
            }
        }
    }

    @Test
    void preV3RevisionsPreserveAbsenceOfGeneratedChannels() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                17L,
                GenerationRevision.V2,
                RngRevision.V1);

        HydrographyField field = new HydrographyGenerationStage().generate(
                genesis,
                constantElevation(bounds, 0),
                syntheticDrainage(bounds));

        for (int y = 0; y <= 4; y++) {
            for (int x = 0; x <= 4; x++) {
                assertFalse(field.isChannelAt(x, y));
            }
        }
    }

    @Test
    void channelAtWorldCeilingIsRejectedBeforeInitialWaterMaterialization() {
        WorldBounds bounds = new WorldBounds(0, 4, 0, 4, -4, 4);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 17L);
        ElevationField ceiling = constantElevation(bounds, bounds.maxZ());
        HydrographyField field = new HydrographyGenerationStage().generate(
                genesis,
                ceiling,
                syntheticDrainage(bounds));

        for (int y = 0; y <= 4; y++) {
            assertFalse(field.isChannelAt(2, y));
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
