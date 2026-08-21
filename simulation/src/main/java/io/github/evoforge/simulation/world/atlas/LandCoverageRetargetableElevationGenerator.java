package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Elevation generator whose expensive Land-independent preparation can be reused when only the
 * semantic Land coverage changes.
 *
 * <p>This contract exists for coordinators such as V15 that must inspect a terrain-dependent fact
 * before they know the exact dry-land compensation. A prepared generator may cache geometry or
 * stochastic fields that are invariant under a Land-only retarget, while every materialization must
 * remain observationally equivalent to a normal generation at the supplied target genesis.</p>
 */
interface LandCoverageRetargetableElevationGenerator extends ElevationGenerator {

    PreparedLandCoverageElevation prepare(WorldGenesis genesis);

    @Override
    default ElevationField generate(WorldGenesis genesis) {
        return prepare(genesis).materialize(genesis);
    }

    @FunctionalInterface
    interface PreparedLandCoverageElevation {
        ElevationField materialize(WorldGenesis targetGenesis);
    }
}
