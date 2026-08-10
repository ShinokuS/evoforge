package io.github.evoforge.simulation.world.mechanics.geometry;

public final class TransitionMask {

    public static final int NONE = 0;

    private static final int CENTER = 1 << 13;
    private static final int RANGE = (1 << 27) - 1;

    public static final int ALL =
            RANGE & ~CENTER;

    private TransitionMask() {
    }

    public static int of(
            int dx,
            int dy,
            int dz) {

        if (dx < -1 || dx > 1
                || dy < -1 || dy > 1
                || dz < -1 || dz > 1
                || dx == 0 && dy == 0 && dz == 0) {
            throw new IllegalArgumentException();
        }

        int index =
                (dz + 1) * 9
                        + (dy + 1) * 3
                        + dx + 1;

        return 1 << index;
    }

    public static boolean contains(
            int mask,
            int dx,
            int dy,
            int dz) {

        return (mask & of(dx, dy, dz)) != 0;
    }

    static void requireValid(
            int mask) {

        if ((mask & ~ALL) != 0) {
            throw new IllegalArgumentException();
        }
    }
}
