package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class WorldAtlasAlgorithmContractTest {

    @Test
    void atlasOrchestrationComposesTypedElevationThenDrainageContracts() {
        WorldBounds bounds = new WorldBounds(-2, 2, -2, 2, -10, 10);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 77L);
        AtomicInteger elevationCalls = new AtomicInteger();
        AtomicInteger drainageCalls = new AtomicInteger();
        ElevationField substitute = constantElevation(bounds, 3);
        ElevationGenerator elevationAlgorithm = requestedGenesis -> {
            assertSame(genesis, requestedGenesis);
            elevationCalls.incrementAndGet();
            return substitute;
        };
        DrainageGenerator drainageAlgorithm = requestedElevation -> {
            assertSame(substitute, requestedElevation);
            drainageCalls.incrementAndGet();
            return new DrainageGenerationStage().generate(requestedElevation);
        };

        WorldAtlas atlas = new WorldAtlasGenerator(
                elevationAlgorithm, drainageAlgorithm).generate(genesis);

        assertSame(substitute, atlas.elevation());
        assertEquals(1, elevationCalls.get());
        assertEquals(1, drainageCalls.get());
        assertEquals(3, atlas.elevation().elevationAt(0, 0));
        assertEquals(
                3L * ElevationField.SUBUNITS_PER_CELL,
                atlas.elevation().elevationSubunitsAt(0, 0));
        assertSame(bounds, atlas.drainage().bounds());
    }

    @Test
    void orchestrationRejectsMissingAlgorithmsAndBrokenAlgorithmOutput() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 1, -2, 2);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 1L);
        ElevationGenerator elevation = ignored -> constantElevation(bounds, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new WorldAtlasGenerator((ElevationGenerator) null));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldAtlasGenerator(elevation, null));
        assertThrows(IllegalStateException.class,
                () -> new WorldAtlasGenerator(ignored -> null).generate(genesis));
        assertThrows(IllegalStateException.class,
                () -> new WorldAtlasGenerator(elevation, ignored -> null).generate(genesis));
    }

    @Test
    void atlasStillValidatesSubstitutedAlgorithmOutputAgainstGenesis() {
        WorldBounds requested = new WorldBounds(0, 1, 0, 1, -2, 2);
        WorldBounds wrong = new WorldBounds(5, 6, 5, 6, -2, 2);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(requested), 1L);
        ElevationGenerator algorithm = ignored -> constantElevation(wrong, 0);

        assertThrows(IllegalArgumentException.class,
                () -> new WorldAtlasGenerator(algorithm).generate(genesis));
    }

    private static ElevationField constantElevation(WorldBounds bounds, int elevation) {
        return new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                if (!contains(x, y)) {
                    throw new IllegalArgumentException("position outside test elevation field");
                }
                return elevation;
            }
        };
    }
}
