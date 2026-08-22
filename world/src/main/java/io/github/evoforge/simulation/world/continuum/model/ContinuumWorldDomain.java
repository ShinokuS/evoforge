package io.github.evoforge.simulation.world.continuum.model;

/** Logical Continuum address space. It describes coordinates; it does not allocate world cells. */
public record ContinuumWorldDomain(long width, long height) {
    public ContinuumWorldDomain {
        if (width <= 0L) {
            throw new IllegalArgumentException("width must be > 0");
        }
        if (height <= 0L) {
            throw new IllegalArgumentException("height must be > 0");
        }
    }

    public boolean contains(long x, long y) {
        return x >= 0L && y >= 0L && x < width && y < height;
    }
}
