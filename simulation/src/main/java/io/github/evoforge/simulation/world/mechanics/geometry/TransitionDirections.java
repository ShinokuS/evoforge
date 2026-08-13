package io.github.evoforge.simulation.world.mechanics.geometry;

/** Canonical deterministic order of the 26 immediate 3D transition directions. */
public final class TransitionDirections {

    public static final int COUNT = 26;

    private static final int[] DX = new int[COUNT];
    private static final int[] DY = new int[COUNT];
    private static final int[] DZ = new int[COUNT];
    private static final int[] MASK = new int[COUNT];

    static {
        int index = 0;

        for (int changedAxes = 1; changedAxes <= 3; changedAxes++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }

                        int changed =
                                (dx == 0 ? 0 : 1)
                                        + (dy == 0 ? 0 : 1)
                                        + (dz == 0 ? 0 : 1);

                        if (changed != changedAxes) {
                            continue;
                        }

                        DX[index] = dx;
                        DY[index] = dy;
                        DZ[index] = dz;
                        MASK[index] = TransitionMask.of(dx, dy, dz);
                        index++;
                    }
                }
            }
        }

        if (index != COUNT) {
            throw new ExceptionInInitializerError(
                    "unexpected immediate transition direction count: " + index);
        }
    }

    private TransitionDirections() {
    }

    public static int dx(int index) {
        requireIndex(index);
        return DX[index];
    }

    public static int dy(int index) {
        requireIndex(index);
        return DY[index];
    }

    public static int dz(int index) {
        requireIndex(index);
        return DZ[index];
    }

    public static int mask(int index) {
        requireIndex(index);
        return MASK[index];
    }

    private static void requireIndex(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IndexOutOfBoundsException(
                    "transition direction index: " + index);
        }
    }
}
