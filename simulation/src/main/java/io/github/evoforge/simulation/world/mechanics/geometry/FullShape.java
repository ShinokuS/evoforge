package io.github.evoforge.simulation.world.mechanics.geometry;

public final class FullShape
        implements Shape {

    public static final FullShape INSTANCE =
            new FullShape();

    private static final int HORIZONTAL =
            TransitionMask.of(-1, -1, 0)
                    | TransitionMask.of(0, -1, 0)
                    | TransitionMask.of(1, -1, 0)
                    | TransitionMask.of(-1, 0, 0)
                    | TransitionMask.of(1, 0, 0)
                    | TransitionMask.of(-1, 1, 0)
                    | TransitionMask.of(0, 1, 0)
                    | TransitionMask.of(1, 1, 0);

    private static final int UP =
            TransitionMask.of(0, 0, 1);

    private static final int DOWN =
            TransitionMask.of(0, 0, -1);

    private static final long TOP_PORTS =
            TransitionPorts.departuresOnly(HORIZONTAL);

    private FullShape() {
    }

    @Override
    public long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeZ != 1
                || relativeX < -1 || relativeX > 1
                || relativeY < -1 || relativeY > 1) {
            return TransitionPorts.NONE;
        }

        if (relativeX == 0 && relativeY == 0) {
            return TOP_PORTS;
        }

        return TransitionPorts.arrivalsOnly(
                TransitionMask.of(
                        -relativeX,
                        -relativeY,
                        0));
    }

    @Override
    public int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeX == 0 && relativeY == 0) {
            if (relativeZ == -1) {
                return UP;
            }

            if (relativeZ == 1) {
                return DOWN;
            }
        }

        if (relativeZ != 0
                || relativeX < -1 || relativeX > 1
                || relativeY < -1 || relativeY > 1) {
            return TransitionMask.NONE;
        }

        if (relativeX == 0 && relativeY == 0) {
            return HORIZONTAL;
        }

        int towardX = -relativeX;
        int towardY = -relativeY;

        if (towardX != 0 && towardY != 0) {
            return TransitionMask.of(
                    towardX,
                    towardY,
                    0);
        }

        int blocks = TransitionMask.NONE;

        if (towardX != 0) {
            blocks |= TransitionMask.of(towardX, -1, 0);
            blocks |= TransitionMask.of(towardX, 0, 0);
            blocks |= TransitionMask.of(towardX, 1, 0);
        } else {
            blocks |= TransitionMask.of(-1, towardY, 0);
            blocks |= TransitionMask.of(0, towardY, 0);
            blocks |= TransitionMask.of(1, towardY, 0);
        }

        return blocks;
    }
}
