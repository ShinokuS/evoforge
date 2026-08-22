package io.github.evoforge.simulation.world.continuum.map;

/** Replaceable generator of derived map tiles. */
@FunctionalInterface
public interface ContinuumMapTileGenerator {
    ContinuumMapTile generate(ContinuumMapTileKey key);
}
