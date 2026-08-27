package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Revision-router compatibility facade for V12.
 *
 * <p>New composition code should depend on {@link ElevationGenerator} and may use
 * {@link V12BaseTerrainGenerator#standard()} directly. Legacy revision routing remains stable here
 * so V1-V11 compatibility code does not need to know V12 calibration internals.</p>
 */
final class V12LandformElevationGenerator {
    private static final ElevationGenerator STANDARD = V12BaseTerrainGenerator.standard();

    private V12LandformElevationGenerator() {
    }

    static ElevationField generate(WorldGenesis genesis) {
        return STANDARD.generate(genesis);
    }
}
