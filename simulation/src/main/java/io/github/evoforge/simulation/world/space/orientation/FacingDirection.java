package io.github.evoforge.simulation.world.space.orientation;

/** Immutable horizontal facing on the simulation grid. */
public record FacingDirection(int x, int y) {

    public static final FacingDirection EAST = new FacingDirection(1, 0);

    public FacingDirection {
        if (x < -1 || x > 1 || y < -1 || y > 1 || (x == 0 && y == 0)) {
            throw new IllegalArgumentException("facing must be a non-zero unit grid direction");
        }
    }

    public static FacingDirection of(int x, int y) {
        return new FacingDirection(Integer.compare(x, 0), Integer.compare(y, 0));
    }
}
