package io.github.evoforge.simulation.world.geophysics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class FiniteMacroGeophysicalFieldTest {
    private static final long SIDE = 16_000_000L;
    private static final long REVISION = 1L;

    @Test
    void everyPhysicalEdgeIsOceanAcrossRepresentativeProfilesAndSeeds() {
        long[] seeds = {0x45A10F0E2026L, 76_558_044_635_174L, 0x0123456789ABCDEFL};
        MacroGeophysicsPreset[] presets = {
            MacroGeophysicsPreset.SUPERCONTINENT,
            MacroGeophysicsPreset.BALANCED,
            MacroGeophysicsPreset.ARCHIPELAGO,
            MacroGeophysicsPreset.OCEANIC
        };

        for (MacroGeophysicsPreset preset : presets) {
            for (long seed : seeds) {
                MacroGeophysicalModel finite = MacroGeophysics.createFinite(
                        seed, REVISION, preset.definition(), SIDE, SIDE);
                for (long coordinate = 0L; coordinate < SIDE; coordinate += 125_000L) {
                    assertTrue(finite.elevationAt(0L, coordinate) < 0d);
                    assertTrue(finite.elevationAt(SIDE - 1L, coordinate) < 0d);
                    assertTrue(finite.elevationAt(coordinate, 0L) < 0d);
                    assertTrue(finite.elevationAt(coordinate, SIDE - 1L) < 0d);
                }
            }
        }
    }

    @Test
    void existingSourceOceanIsBitIdentical() {
        long seed = 76_558_044_635_174L;
        MacroGeophysicalModel source = MacroGeophysics.create(
                seed, REVISION, MacroGeophysicsPreset.SUPERCONTINENT.definition());
        MacroGeophysicalModel finite = MacroGeophysics.createFinite(
                seed, REVISION, MacroGeophysicsPreset.SUPERCONTINENT.definition(), SIDE, SIDE);

        int compared = 0;
        for (long y = 250_000L; y < SIDE - 250_000L; y += 375_000L) {
            for (long x = 250_000L; x < SIDE - 250_000L; x += 375_000L) {
                double sourceElevation = source.elevationAt(x, y);
                if (sourceElevation <= 0d) {
                    assertEquals(
                            Double.doubleToLongBits(sourceElevation),
                            Double.doubleToLongBits(finite.elevationAt(x, y)));
                    compared++;
                }
            }
        }
        assertTrue(compared > 100, "representative world should expose source ocean samples");
    }

    @Test
    void acceptedInteriorLandRemainsBitIdenticalWhenFinitePhaseStillAcceptsLand() {
        long seed = 76_558_044_635_174L;
        MacroGeophysicalModel source = MacroGeophysics.create(
                seed, REVISION, MacroGeophysicsPreset.SUPERCONTINENT.definition());
        MacroGeophysicalModel finite = MacroGeophysics.createFinite(
                seed, REVISION, MacroGeophysicsPreset.SUPERCONTINENT.definition(), SIDE, SIDE);

        int unchangedLand = 0;
        for (long y = SIDE / 4L; y <= SIDE * 3L / 4L; y += 200_000L) {
            for (long x = SIDE / 4L; x <= SIDE * 3L / 4L; x += 200_000L) {
                double sourceElevation = source.elevationAt(x, y);
                double finiteElevation = finite.elevationAt(x, y);
                if (sourceElevation > 0d && finiteElevation > 0d) {
                    assertEquals(
                            Double.doubleToLongBits(sourceElevation),
                            Double.doubleToLongBits(finiteElevation));
                    unchangedLand++;
                }
            }
        }
        assertTrue(unchangedLand > 20, "representative interior should retain accepted land");
    }

    @Test
    void finiteTopologyDoesNotRewriteStructuralGeophysics() {
        long seed = 0x0123456789ABCDEFL;
        MacroGeophysicalModel source = MacroGeophysics.create(
                seed, REVISION, MacroGeophysicsPreset.BALANCED.definition());
        MacroGeophysicalModel finite = MacroGeophysics.createFinite(
                seed, REVISION, MacroGeophysicsPreset.BALANCED.definition(), SIDE, SIDE);

        for (long y = 1_000_000L; y < SIDE; y += 2_750_000L) {
            for (long x = 1_000_000L; x < SIDE; x += 2_750_000L) {
                assertEquals(source.structureAt(x, y), finite.structureAt(x, y));
            }
        }
    }
}
