package io.github.evoforge.simulation.world.surface;

/** One vertically sky-exposed physical surface resolved for an XY column. */
public record SkySurface(
        int x,
        int y,
        int z,
        Kind kind) {

    public SkySurface {
        if (kind == null) {
            throw new IllegalArgumentException(
                    "sky surface kind must not be null");
        }
    }

    public enum Kind {
        TERRAIN,
        WATER
    }
}
