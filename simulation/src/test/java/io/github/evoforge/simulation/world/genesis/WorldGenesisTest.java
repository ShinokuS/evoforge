package io.github.evoforge.simulation.world.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenesisTest {

    @Test
    void genesisContainsOnlyStructuralInputsSeedAndHumanAuthoredDefinition() {
        WorldSpec spec = new WorldSpec(new WorldBounds(-20, 30, -10, 40, -5, 12));
        WorldGenerationIntent intent = definition();

        WorldGenesis genesis = new WorldGenesis(spec, Long.MIN_VALUE, intent);

        assertEquals(spec, genesis.spec());
        assertEquals(Long.MIN_VALUE, genesis.masterSeed());
        assertEquals(intent, genesis.generationIntent());
    }

    @Test
    void genesisRejectsMissingRequiredInputs() {
        WorldSpec spec = new WorldSpec(new WorldBounds(0, 0, 0, 0, 0, 0));
        WorldGenerationIntent intent = definition();

        assertThrows(IllegalArgumentException.class, () -> new WorldSpec(null));
        assertThrows(IllegalArgumentException.class, () -> new WorldGenesis(null, 0L, intent));
        assertThrows(IllegalArgumentException.class, () -> new WorldGenesis(spec, 0L, null));
    }

    private static WorldGenerationIntent definition() {
        return new WorldGenerationIntent(
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.35),
                NormalizedValue.of(0.6),
                NormalizedValue.of(0.45),
                NormalizedValue.of(0.5),
                NormalizedValue.of(0.35),
                new MountainIntent(
                        NormalizedValue.of(0.35),
                        NormalizedValue.of(0.52),
                        NormalizedValue.of(0.5),
                        NormalizedValue.of(0.55),
                        NormalizedValue.of(0.6),
                        NormalizedValue.of(0.18)));
    }
}
