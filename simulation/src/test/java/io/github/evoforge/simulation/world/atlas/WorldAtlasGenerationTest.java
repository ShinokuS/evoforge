package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldAtlasGenerationTest {

    @Test
    void elevationV1MatchesFrozenRepresentativeSamples() {
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(new WorldBounds(-32, 31, -32, 31, -32, 32)),
                123_456_789L);

        ElevationField elevation = new WorldAtlasGenerator().generate(genesis).elevation();

        assertEquals(-3, elevation.elevationAt(-32, -32));
        assertEquals(-3, elevation.elevationAt(-16, 7));
        assertEquals(-2, elevation.elevationAt(0, 0));
        assertEquals(-1, elevation.elevationAt(15, -9));
        assertEquals(5, elevation.elevationAt(31, 31));
    }

    @Test
    void identicalGenesisProducesIdenticalElevationEverywhere() {
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(new WorldBounds(-12, 13, -9, 14, -20, 20)),
                77L);
        ElevationField first = new WorldAtlasGenerator().generate(genesis).elevation();
        ElevationField second = new WorldAtlasGenerator().generate(genesis).elevation();

        for (int y = -9; y <= 14; y++) {
            for (int x = -12; x <= 13; x++) {
                assertEquals(first.elevationAt(x, y), second.elevationAt(x, y));
            }
        }
    }

    @Test
    void overlappingWorldBoundsKeepSameGlobalElevationFacts() {
        WorldGenesis leftGenesis = WorldGenesis.current(
                new WorldSpec(new WorldBounds(-20, 10, -10, 10, -30, 30)),
                55L);
        WorldGenesis rightGenesis = WorldGenesis.current(
                new WorldSpec(new WorldBounds(0, 30, -10, 10, -30, 30)),
                55L);

        ElevationField left = new WorldAtlasGenerator().generate(leftGenesis).elevation();
        ElevationField right = new WorldAtlasGenerator().generate(rightGenesis).elevation();

        for (int y = -10; y <= 10; y++) {
            for (int x = 0; x <= 10; x++) {
                assertEquals(left.elevationAt(x, y), right.elevationAt(x, y));
            }
        }
    }

    @Test
    void elevationStaysInsideReservedVerticalBandAndUsesMoreThanOneHeight() {
        WorldBounds bounds = new WorldBounds(-32, 31, -32, 31, -40, 40);
        ElevationField elevation = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(new WorldSpec(bounds), 991L))
                .elevation();
        int minimum = Integer.MAX_VALUE;
        int maximum = Integer.MIN_VALUE;

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                int value = elevation.elevationAt(x, y);
                minimum = Math.min(minimum, value);
                maximum = Math.max(maximum, value);
                assertTrue(value >= -20 && value <= 20);
            }
        }

        assertTrue(maximum > minimum);
    }

    @Test
    void differentSeedChangesGeneratedElevationFacts() {
        WorldSpec spec = new WorldSpec(new WorldBounds(-16, 16, -16, 16, -20, 20));
        ElevationField first = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(spec, 1L)).elevation();
        ElevationField second = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(spec, 2L)).elevation();

        assertNotEquals(first.elevationAt(0, 0), second.elevationAt(0, 0));
    }

    @Test
    void atlasRejectsUnsupportedGeneratorRevisionAndOutOfBoundsLookup() {
        WorldSpec spec = new WorldSpec(new WorldBounds(0, 3, 0, 3, -5, 5));
        WorldGenesis unsupported = new WorldGenesis(
                spec,
                1L,
                GenerationRevision.of("test:worldgen-v2"),
                RngRevision.V1);

        assertThrows(IllegalArgumentException.class,
                () -> new WorldAtlasGenerator().generate(unsupported));

        ElevationField elevation = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(spec, 1L)).elevation();
        assertThrows(IllegalArgumentException.class,
                () -> elevation.elevationAt(4, 0));
    }
}
