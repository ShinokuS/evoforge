package io.github.evoforge.simulation.world.geometry;

public final class GridTransitionLength {

    public static final int SCALE = 1000;
    public static final int CARDINAL = 1000;
    public static final int DOUBLE_DIAGONAL = 1414;
    public static final int TRIPLE_DIAGONAL = 1732;

    private GridTransitionLength() {
    }

    public static int units(
            int dx,
            int dy,
            int dz) {

        TransitionMask.of(
                dx,
                dy,
                dz);

        int axes = 0;

        if (dx != 0) {
            axes++;
        }
        if (dy != 0) {
            axes++;
        }
        if (dz != 0) {
            axes++;
        }

        return switch (axes) {
            case 1 -> CARDINAL;
            case 2 -> DOUBLE_DIAGONAL;
            case 3 -> TRIPLE_DIAGONAL;
            default -> throw new IllegalStateException(
                    "invalid transition axis count: " + axes);
        };
    }
}
