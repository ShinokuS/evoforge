package io.github.evoforge.simulation.world.genesis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class GenerationRandomTest {
    private static final WorldSpec SPEC =
            new WorldSpec(new WorldBounds(Integer.MIN_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MAX_VALUE,
                    Integer.MIN_VALUE, Integer.MAX_VALUE));

    @Test
    void rngV1MatchesFrozenGoldenVectors() {
        assertSample(
                0L,
                "world:elevation",
                "world:base",
                0, 0, 0, 0,
                -393_299_259_922_411_962L);
        assertSample(
                123_456_789L,
                "world:elevation",
                "world:base",
                12, -7, 3, 0,
                -9_148_604_890_664_678_716L);
        assertSample(
                -1L,
                "world:climate",
                "world:temperature",
                Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 42,
                -2_594_573_980_885_016_132L);
        assertSample(
                0x0123456789abcdefL,
                "world:geology",
                "world:strata",
                100, 200, -30, 999,
                -2_880_408_368_160_197_730L);
    }

    @Test
    void samplingOrderDoesNotBecomeHiddenRandomState() {
        GenerationRandom first = GenerationRandom.from(WorldGenesis.current(SPEC, 99L));
        GenerationStageId stage = GenerationStageId.of("world:elevation");
        GenerationPurposeId base = GenerationPurposeId.of("world:base");
        GenerationPurposeId detail = GenerationPurposeId.of("world:detail");

        long aThen = first.sampleLong(stage, base, 3, 4, 5, 0);
        first.sampleLong(stage, detail, 100, 200, 300, 17);
        long bThen = first.sampleLong(stage, base, -8, 9, 10, 2);

        GenerationRandom second = GenerationRandom.from(WorldGenesis.current(SPEC, 99L));
        long bFirst = second.sampleLong(stage, base, -8, 9, 10, 2);
        second.sampleLong(stage, detail, -500, -600, -700, 4);
        long aLast = second.sampleLong(stage, base, 3, 4, 5, 0);

        assertEquals(aThen, aLast);
        assertEquals(bThen, bFirst);
    }

    @Test
    void semanticAndSpatialScopeComponentsAreIndependentInputs() {
        GenerationRandom random = GenerationRandom.from(WorldGenesis.current(SPEC, 123L));
        GenerationStageId stage = GenerationStageId.of("world:elevation");
        GenerationPurposeId purpose = GenerationPurposeId.of("world:base");
        long base = random.sampleLong(stage, purpose, 1, 2, 3, 0);

        assertNotEquals(base, random.sampleLong(GenerationStageId.of("world:climate"), purpose, 1, 2, 3, 0));
        assertNotEquals(base, random.sampleLong(stage, GenerationPurposeId.of("world:detail"), 1, 2, 3, 0));
        assertNotEquals(base, random.sampleLong(stage, purpose, 2, 2, 3, 0));
        assertNotEquals(base, random.sampleLong(stage, purpose, 1, 3, 3, 0));
        assertNotEquals(base, random.sampleLong(stage, purpose, 1, 2, 4, 0));
        assertNotEquals(base, random.sampleLong(stage, purpose, 1, 2, 3, 1));
        assertNotEquals(base, GenerationRandom.from(WorldGenesis.current(SPEC, 124L))
                .sampleLong(stage, purpose, 1, 2, 3, 0));
    }

    @Test
    void executableRngContractRejectsUnknownRevisionInsteadOfSilentlyChangingWorld() {
        WorldGenesis unknown = new WorldGenesis(
                SPEC,
                1L,
                GenerationRevision.V1,
                RngRevision.of("test:rng-v2"));

        assertThrows(IllegalArgumentException.class, () -> GenerationRandom.from(unknown));
    }

    @Test
    void samplerRejectsMissingScopeAndNegativeOrdinal() {
        GenerationRandom random = GenerationRandom.from(WorldGenesis.current(SPEC, 0L));
        GenerationStageId stage = GenerationStageId.of("world:elevation");
        GenerationPurposeId purpose = GenerationPurposeId.of("world:base");

        assertThrows(IllegalArgumentException.class,
                () -> GenerationRandom.from(null));
        assertThrows(IllegalArgumentException.class,
                () -> random.sampleLong(null, purpose, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> random.sampleLong(stage, null, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> random.sampleLong(stage, purpose, 0, 0, 0, -1));
    }

    private static void assertSample(
            long seed,
            String stage,
            String purpose,
            int x,
            int y,
            int z,
            long ordinal,
            long expected) {
        GenerationRandom random = GenerationRandom.from(WorldGenesis.current(SPEC, seed));
        assertEquals(expected, random.sampleLong(
                GenerationStageId.of(stage),
                GenerationPurposeId.of(purpose),
                x, y, z, ordinal));
    }
}
