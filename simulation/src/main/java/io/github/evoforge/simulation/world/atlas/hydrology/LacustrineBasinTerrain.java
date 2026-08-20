package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/** Terrain elevation after the dedicated lacustrine-basin morphology contribution. */
public record LacustrineBasinTerrain(
        ElevationField elevation,
        int imprintedBasinCount) {

    public LacustrineBasinTerrain {
        if (elevation == null) throw new IllegalArgumentException("basin terrain elevation must not be null");
        if (imprintedBasinCount < 0) {
            throw new IllegalArgumentException("imprinted basin count must be non-negative");
        }
    }
}
