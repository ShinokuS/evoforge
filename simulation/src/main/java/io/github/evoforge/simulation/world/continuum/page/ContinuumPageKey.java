package io.github.evoforge.simulation.world.continuum.page;

/** Technical page address. A page key is storage/materialization identity, never geography. */
public record ContinuumPageKey(long pageX, long pageY) {
    public ContinuumPageKey {
        if (pageX < 0L || pageY < 0L) {
            throw new IllegalArgumentException("page coordinates must be >= 0");
        }
    }
}
