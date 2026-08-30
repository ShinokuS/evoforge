package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class ElevationGenerationStageTest {

    @Test
    void referenceWorldsStayExactWhileLargePreviewWorldsUseProductionContinuum() {
        assertTrue(ElevationGenerationStage.usesExactReferencePlan(
                new ContinuumWorldDomain(320, 320)));
        assertTrue(ElevationGenerationStage.usesExactReferencePlan(
                new ContinuumWorldDomain(512, 512)));
        assertFalse(ElevationGenerationStage.usesExactReferencePlan(
                new ContinuumWorldDomain(513, 512)));
        assertFalse(ElevationGenerationStage.usesExactReferencePlan(
                new ContinuumWorldDomain(1_000, 1_000)));
        assertFalse(ElevationGenerationStage.usesExactReferencePlan(
                new ContinuumWorldDomain(10_000, 10_000)));

        assertTrue(ElevationGenerationStage.usesPreloadedPreview(
                new ContinuumWorldDomain(512, 512)));
        assertFalse(ElevationGenerationStage.usesPreloadedPreview(
                new ContinuumWorldDomain(513, 513)));
        assertFalse(ElevationGenerationStage.usesPreloadedPreview(
                new ContinuumWorldDomain(1_000, 1_000)));
        assertFalse(ElevationGenerationStage.usesPreloadedPreview(
                new ContinuumWorldDomain(3_000, 3_000)));

        assertThrows(
                IllegalArgumentException.class,
                () -> ElevationGenerationStage.usesExactReferencePlan(null));
    }

    @Test
    void thousandCellPreviewExecutesProductionContinuumThroughBulkElevationContract() {
        WorldBounds bounds = new WorldBounds(-500, 499, -500, 499, -96, 96);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                -4_774_846_722_868_265_927L,
                GenerationRevision.V15,
                RngRevision.V1,
                WorldGenerationIntent.balanced());

        ElevationField elevation = new ElevationGenerationStage().generate(genesis);
        assertEquals(bounds, elevation.bounds());
        assertFalse(elevation instanceof MaterializedElevationField,
                "production preview must stay page-backed instead of copying the whole world");

        long[] first = new long[25];
        long[] repeated = new long[25];
        elevation.fillElevationSubunits(-400, -400, 5, 5, 175L, first);
        elevation.fillElevationSubunits(-400, -400, 5, 5, 175L, repeated);

        assertArrayEquals(first, repeated, "preview bulk sampling must be deterministic");
        boolean anyTerrainSignal = false;
        for (long value : first) {
            if (value != 0L) {
                anyTerrainSignal = true;
                break;
            }
        }
        assertTrue(anyTerrainSignal, "balanced production preview must return authored terrain values");
    }
}
