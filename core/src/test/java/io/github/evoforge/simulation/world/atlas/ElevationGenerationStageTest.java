package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
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
        assertThrows(
                IllegalArgumentException.class,
                () -> ElevationGenerationStage.usesExactReferencePlan(null));
    }
}
