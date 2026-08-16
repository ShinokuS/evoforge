package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class DrainageGenerationStageTest {

    @Test
    void descendingEdgeChainTerminatesInsideClosedWorld() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 0, -10, 10),
                3_000_000L, 2_000_000L, 1_000_000L);

        DrainageField drainage = new DrainageGenerationStage().generate(elevation);

        assertDownstream(drainage, 0, 0, 1, 0);
        assertDownstream(drainage, 1, 0, 2, 0);
        assertFalse(drainage.hasDownstream(2, 0));
        assertEquals(3L, drainage.contributingAreaAt(2, 0));
        assertEquals(2, drainage.terminalXAt(0, 0));
        assertEquals(0, drainage.terminalYAt(0, 0));
        assertThrows(IllegalStateException.class, () -> drainage.downstreamXAt(2, 0));
    }

    @Test
    void preciseGradientDrainsEvenWhenDiscreteTerrainHeightIsFlat() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 0, -2, 2),
                800_000L, 500_000L, 200_000L);
        assertEquals(0, elevation.elevationAt(0, 0));
        assertEquals(0, elevation.elevationAt(1, 0));
        assertEquals(0, elevation.elevationAt(2, 0));

        DrainageField drainage = new DrainageGenerationStage().generate(elevation);

        assertDownstream(drainage, 0, 0, 1, 0);
        assertDownstream(drainage, 1, 0, 2, 0);
        assertFalse(drainage.hasDownstream(2, 0));
    }

    @Test
    void equalFlatWithLowerOutletRoutesTowardOutletWithoutFakeTerminal() {
        ElevationField elevation = field(
                new WorldBounds(0, 3, 0, 0, -2, 2),
                1_000_000L, 1_000_000L, 1_000_000L, 0L);

        DrainageField drainage = new DrainageGenerationStage().generate(elevation);

        assertDownstream(drainage, 0, 0, 1, 0);
        assertDownstream(drainage, 1, 0, 2, 0);
        assertDownstream(drainage, 2, 0, 3, 0);
        assertFalse(drainage.hasDownstream(3, 0));
        assertEquals(4L, drainage.contributingAreaAt(3, 0));
    }

    @Test
    void enclosedFlatBecomesOneDeterministicInternalBasin() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 0, -2, 2),
                1_000_000L, 1_000_000L, 1_000_000L);

        DrainageField drainage = new DrainageGenerationStage().generate(elevation);

        assertFalse(drainage.hasDownstream(0, 0));
        assertDownstream(drainage, 1, 0, 0, 0);
        assertDownstream(drainage, 2, 0, 1, 0);
        assertEquals(3L, drainage.contributingAreaAt(0, 0));
        for (int x = 0; x <= 2; x++) {
            assertEquals(0, drainage.terminalXAt(x, 0));
            assertEquals(0, drainage.terminalYAt(x, 0));
        }
    }

    @Test
    void enclosedDepressionCollectsWholeBasin() {
        ElevationField elevation = field(
                new WorldBounds(0, 2, 0, 2, -2, 6),
                5_000_000L, 4_000_000L, 5_000_000L,
                4_000_000L, 0L, 4_000_000L,
                5_000_000L, 4_000_000L, 5_000_000L);

        DrainageField drainage = new DrainageGenerationStage().generate(elevation);

        assertFalse(drainage.hasDownstream(1, 1));
        assertEquals(9L, drainage.contributingAreaAt(1, 1));
        for (int y = 0; y <= 2; y++) {
            for (int x = 0; x <= 2; x++) {
                assertEquals(1, drainage.terminalXAt(x, y));
                assertEquals(1, drainage.terminalYAt(x, y));
            }
        }
    }

    @Test
    void generatedTopologyNeverLeavesBoundsAndEveryColumnReachesATerminal() {
        WorldBounds bounds = new WorldBounds(-12, 13, -9, 14, -20, 20);
        WorldAtlas atlas = new WorldAtlasGenerator().generate(
                io.github.evoforge.simulation.world.genesis.WorldGenesis.current(
                        new io.github.evoforge.simulation.world.genesis.WorldSpec(bounds),
                        77L));
        DrainageField drainage = atlas.drainage();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertTrue(drainage.contributingAreaAt(x, y) >= 1L);
                assertTrue(drainage.contains(
                        drainage.terminalXAt(x, y), drainage.terminalYAt(x, y)));
                if (drainage.hasDownstream(x, y)) {
                    assertTrue(drainage.contains(
                            drainage.downstreamXAt(x, y), drainage.downstreamYAt(x, y)));
                }
            }
        }
    }

    private static ElevationField field(WorldBounds bounds, long... elevationSubunits) {
        return new DenseElevationField(bounds, elevationSubunits);
    }

    private static void assertDownstream(
            DrainageField drainage,
            int x,
            int y,
            int expectedX,
            int expectedY) {
        assertTrue(drainage.hasDownstream(x, y));
        assertEquals(expectedX, drainage.downstreamXAt(x, y));
        assertEquals(expectedY, drainage.downstreamYAt(x, y));
    }
}
