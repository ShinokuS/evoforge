package io.github.evoforge.simulation.world.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldGenesisTest {

    @Test
    void currentGenesisCapturesRequestedSpecSeedAndExplicitRevisions() {
        WorldSpec spec = new WorldSpec(new WorldBounds(-20, 30, -10, 40, -5, 12));

        WorldGenesis genesis = WorldGenesis.current(spec, Long.MIN_VALUE);

        assertEquals(spec, genesis.spec());
        assertEquals(Long.MIN_VALUE, genesis.masterSeed());
        assertEquals(GenerationRevision.V4, genesis.generationRevision());
        assertEquals(RngRevision.V1, genesis.rngRevision());
    }

    @Test
    void provenanceAllowsOlderOrFutureRevisionMetadataToRemainRepresentable() {
        WorldSpec spec = new WorldSpec(new WorldBounds(0, 1, 0, 1, 0, 1));
        WorldGenesis genesis = new WorldGenesis(
                spec,
                7L,
                GenerationRevision.of("test:worldgen-v17"),
                RngRevision.of("test:rng-v4"));

        assertEquals("test:worldgen-v17", genesis.generationRevision().value());
        assertEquals("test:rng-v4", genesis.rngRevision().value());
    }

    @Test
    void genesisRejectsMissingRequiredMetadata() {
        WorldSpec spec = new WorldSpec(new WorldBounds(0, 0, 0, 0, 0, 0));

        assertThrows(IllegalArgumentException.class, () -> new WorldSpec(null));
        assertThrows(IllegalArgumentException.class, () -> new WorldGenesis(
                null, 0L, GenerationRevision.V1, RngRevision.V1));
        assertThrows(IllegalArgumentException.class, () -> new WorldGenesis(
                spec, 0L, null, RngRevision.V1));
        assertThrows(IllegalArgumentException.class, () -> new WorldGenesis(
                spec, 0L, GenerationRevision.V1, null));
    }

    @Test
    void stableGenesisIdentifiersUseNamespacedKeys() {
        assertEquals("world:elevation", GenerationStageId.of("world:elevation").value());
        assertEquals("world:base", GenerationPurposeId.of("world:base").value());
        assertEquals("evoforge:worldgen-v1", GenerationRevision.V1.value());
        assertEquals("evoforge:worldgen-v2", GenerationRevision.V2.value());
        assertEquals("evoforge:worldgen-v3", GenerationRevision.V3.value());
        assertEquals("evoforge:worldgen-v4", GenerationRevision.V4.value());

        assertThrows(IllegalArgumentException.class, () -> GenerationStageId.of("Elevation"));
        assertThrows(IllegalArgumentException.class, () -> GenerationPurposeId.of("world base"));
        assertThrows(IllegalArgumentException.class, () -> GenerationRevision.of("v1"));
        assertThrows(IllegalArgumentException.class, () -> RngRevision.of(""));
    }
}
