package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.MountainIntent;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class MountainAbundanceCoverageTest {
    private static final WorldBounds WORLD = new WorldBounds(-128, 127, -128, 127, -12, 96);
    private static final WorldBounds BASE = new WorldBounds(-128, 127, -128, 127, -12, 12);
    private static final long SEED = 9_137L;
    private static final long MEANINGFUL_UPLIFT = ElevationField.SUBUNITS_PER_CELL / 4L;

    @Test
    void abundanceControlsRealMountainFootprintInsteadOfRawCandidateProbability() {
        double sparse = affectedLandFraction(intent(150_000, 600_000, 500_000));
        double balanced = affectedLandFraction(intent(350_000, 600_000, 500_000));
        double abundant = affectedLandFraction(intent(650_000, 600_000, 500_000));
        String values = "sparse=" + sparse + ", balanced=" + balanced + ", abundant=" + abundant;

        assertTrue(sparse <= balanced + 1.0e-9,
                "raising abundance must never reduce real mountain coverage: " + values);
        assertTrue(balanced <= abundant + 1.0e-9,
                "raising abundance must remain monotonic even when source count is discrete: " + values);
        assertTrue(abundant >= sparse + 0.05,
                "low and high abundance must materially change real mountain coverage: " + values);
        assertTrue(balanced < 0.60,
                "ordinary abundance must leave substantial non-mountain land: " + values);
        assertTrue(abundant < 0.85,
                "even high abundance must not silently become a near-total mountain carpet: " + values);
    }

    @Test
    void scaleChangesStructureSizeWithoutTakingOwnershipOfTotalMountainCoverage() {
        double smallStructures = affectedLandFraction(intent(350_000, 600_000, 200_000));
        double largeStructures = affectedLandFraction(intent(350_000, 600_000, 800_000));

        assertTrue(Math.abs(smallStructures - largeStructures) < 0.22,
                "Abundance, not Scale, must remain the primary control over mountain coverage; small="
                        + smallStructures + ", large=" + largeStructures);
    }

    private static double affectedLandFraction(WorldGenerationIntent intent) {
        ElevationField base = V12BaseTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(BASE),
                SEED,
                GenerationRevision.V12,
                RngRevision.V1,
                intent));
        ElevationField mountains = V13MountainTerrainGenerator.standard().generate(new WorldGenesis(
                new WorldSpec(WORLD),
                SEED,
                GenerationRevision.V13,
                RngRevision.V1,
                intent));

        long land = 0L;
        long affected = 0L;
        for (int y = WORLD.minY(); y <= WORLD.maxY(); y++) {
            for (int x = WORLD.minX(); x <= WORLD.maxX(); x++) {
                long baseHeight = base.elevationSubunitsAt(x, y);
                if (baseHeight <= 0L) continue;
                land++;
                if (mountains.elevationSubunitsAt(x, y) - baseHeight >= MEANINGFUL_UPLIFT) affected++;
            }
        }
        return land == 0L ? 0.0 : affected / (double) land;
    }

    private static WorldGenerationIntent intent(int abundance, int height, int scale) {
        return new WorldGenerationIntent(
                normalized(800_000),
                normalized(750_000),
                normalized(250_000),
                normalized(600_000),
                normalized(450_000),
                normalized(500_000),
                normalized(350_000),
                new MountainIntent(
                        normalized(abundance),
                        normalized(height),
                        normalized(scale),
                        normalized(550_000),
                        normalized(500_000),
                        true,
                        normalized(180_000)));
    }

    private static NormalizedValue normalized(int ppm) {
        return NormalizedValue.ofPartsPerMillion(ppm);
    }
}
