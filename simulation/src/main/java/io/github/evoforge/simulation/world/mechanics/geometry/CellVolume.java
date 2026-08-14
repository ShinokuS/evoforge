package io.github.evoforge.simulation.world.mechanics.geometry;

/**
 * Deterministic fixed-point volume scale for one discrete world cell.
 *
 * <p>The scale is geometric and material-agnostic. A value describes a fraction of one
 * cell volume; it is not a promise about metres, litres, density or a particular fluid.
 */
public final class CellVolume {

    public static final int EMPTY = 0;
    public static final int FULL = 1_000_000;

    private CellVolume() {
    }

    public static int requireValid(
            int volume) {

        if (volume < EMPTY || volume > FULL) {
            throw new IllegalArgumentException(
                    "cell volume must be in ["
                            + EMPTY
                            + ", "
                            + FULL
                            + "]: "
                            + volume);
        }

        return volume;
    }
}
