package io.github.evoforge.simulation.world.terrain.field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V12ContinuumSlopeCalibration;
import org.junit.jupiter.api.Test;

final class V12SlopeLimitedPageSourceTest {
    private static final long CELL = TerrainElevationField.SUBUNITS_PER_CELL;

    @Test
    void alreadyCompliantTerrainIsBitIdentical() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(17, 11);
        TerrainElevationField source = (x, y) -> CELL + x * 100_000L + y * 50_000L;
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                source,
                new V12ContinuumSlopeCalibration(200_000L, 4L * CELL, 15));
        ContinuumSampleWindow window = new ContinuumSampleWindow(3, 2, 9, 6, 1);
        ContinuumScalarPage page = slopes.materialize(window);

        for (int y = 0; y < window.height(); y++) {
            for (int x = 0; x < window.width(); x++) {
                assertEquals(
                        source.elevationSubunitsAt(window.xAt(x), window.yAt(y)),
                        (long) page.sample(x, y));
            }
        }
    }

    @Test
    void isolatedSpikeBecomesStrictlySlopeBoundedWithoutChangingLandMembership() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(21, 21);
        TerrainElevationField source = (x, y) -> x == 10L && y == 10L ? 9L * CELL : CELL;
        long maximumStep = CELL;
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                source,
                new V12ContinuumSlopeCalibration(maximumStep, 9L * CELL, 8));
        ContinuumSampleWindow window = new ContinuumSampleWindow(0, 0, 21, 21, 1);
        ContinuumScalarPage page = slopes.materialize(window);

        long center = (long) page.sample(10, 10);
        assertTrue(center > CELL && center < 9L * CELL);
        for (int y = 0; y < 21; y++) {
            for (int x = 0; x < 21; x++) {
                long value = (long) page.sample(x, y);
                assertTrue(value > 0L, "slope projection must preserve land membership");
                if (x + 1 < 21) {
                    assertTrue(Math.abs((long) page.sample(x + 1, y) - value) <= maximumStep);
                }
                if (y + 1 < 21) {
                    assertTrue(Math.abs((long) page.sample(x, y + 1) - value) <= maximumStep);
                }
            }
        }
    }

    @Test
    void waterDisconnectsSlopeInfluenceBetweenLandComponents() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(15, 5);
        TerrainElevationField source = (x, y) -> {
            if (x == 7L) return -1L;
            return x < 7L ? CELL : 9L * CELL;
        };
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                source,
                new V12ContinuumSlopeCalibration(CELL, 9L * CELL, 8));
        ContinuumScalarPage page = slopes.materialize(new ContinuumSampleWindow(0, 0, 15, 5, 1));

        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 7; x++) assertEquals(CELL, (long) page.sample(x, y));
            assertEquals(-1L, (long) page.sample(7, y));
            for (int x = 8; x < 15; x++) assertEquals(9L * CELL, (long) page.sample(x, y));
        }
    }

    @Test
    void overlappingWindowsReturnIdenticalSharedCoordinates() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(40, 32);
        TerrainElevationField source = (x, y) -> {
            if (x < 2 || y < 2 || x > 37 || y > 29) return -1L;
            long broad = CELL + (x * 173_113L + y * 97_531L) % (7L * CELL);
            if (x == 20L && y == 16L) broad = 12L * CELL;
            return broad;
        };
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                source,
                new V12ContinuumSlopeCalibration(800_000L, 12L * CELL, 14));
        ContinuumSampleWindow firstWindow = new ContinuumSampleWindow(8, 6, 20, 18, 1);
        ContinuumSampleWindow secondWindow = new ContinuumSampleWindow(15, 10, 19, 17, 1);
        ContinuumScalarPage first = slopes.materialize(firstWindow);
        ContinuumScalarPage second = slopes.materialize(secondWindow);

        for (long worldY = 10; worldY <= 23; worldY++) {
            for (long worldX = 15; worldX <= 27; worldX++) {
                int firstX = Math.toIntExact(worldX - firstWindow.minX());
                int firstY = Math.toIntExact(worldY - firstWindow.minY());
                int secondX = Math.toIntExact(worldX - secondWindow.minX());
                int secondY = Math.toIntExact(worldY - secondWindow.minY());
                assertEquals(
                        (long) first.sample(firstX, firstY),
                        (long) second.sample(secondX, secondY),
                        "shared coordinate changed with requested window");
            }
        }
    }

    @Test
    void steppedWindowMatchesUnitResolutionAtSampledCoordinates() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(18, 18);
        TerrainElevationField source = (x, y) -> CELL + (x + y) * 500_000L;
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                source,
                new V12ContinuumSlopeCalibration(600_000L, 18L * CELL, 29));
        ContinuumScalarPage unit = slopes.materialize(new ContinuumSampleWindow(2, 2, 13, 13, 1));
        ContinuumScalarPage stepped = slopes.materialize(new ContinuumSampleWindow(2, 2, 7, 7, 2));

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 7; x++) {
                assertEquals((long) unit.sample(x * 2, y * 2), (long) stepped.sample(x, y));
            }
        }
    }

    @Test
    void validatesBoundsAndCalibratedHeightContract() {
        ContinuumWorldDomain domain = new ContinuumWorldDomain(8, 8);
        V12SlopeLimitedPageSource slopes = new V12SlopeLimitedPageSource(
                domain,
                (x, y) -> 5L * CELL,
                new V12ContinuumSlopeCalibration(CELL, 4L * CELL, 3));

        assertThrows(
                IllegalArgumentException.class,
                () -> slopes.materialize(new ContinuumSampleWindow(7, 7, 2, 2, 1)));
        assertThrows(
                IllegalStateException.class,
                () -> slopes.materialize(new ContinuumSampleWindow(0, 0, 2, 2, 1)));
    }
}
