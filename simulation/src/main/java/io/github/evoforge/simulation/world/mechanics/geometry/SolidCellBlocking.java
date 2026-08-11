package io.github.evoforge.simulation.world.mechanics.geometry;

final class SolidCellBlocking {

    static final int HORIZONTAL =
            TransitionMask.of(-1, -1, 0)
                    | TransitionMask.of(0, -1, 0)
                    | TransitionMask.of(1, -1, 0)
                    | TransitionMask.of(-1, 0, 0)
                    | TransitionMask.of(1, 0, 0)
                    | TransitionMask.of(-1, 1, 0)
                    | TransitionMask.of(0, 1, 0)
                    | TransitionMask.of(1, 1, 0);

    private SolidCellBlocking() {
    }

    static int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeX < -1 || relativeX > 1
                || relativeY < -1 || relativeY > 1
                || relativeZ < -1 || relativeZ > 1) {
            return TransitionMask.NONE;
        }

        if (relativeX == 0
                && relativeY == 0
                && relativeZ == 0) {
            return HORIZONTAL;
        }

        int towardX = -relativeX;
        int towardY = -relativeY;
        int towardZ = -relativeZ;

        int blocks =
                TransitionMask.of(
                        towardX,
                        towardY,
                        towardZ);

        if (relativeZ != 0
                || towardX != 0 && towardY != 0) {
            return blocks;
        }

        if (towardX != 0) {
            blocks |= TransitionMask.of(towardX, -1, 0);
            blocks |= TransitionMask.of(towardX, 1, 0);
        } else {
            blocks |= TransitionMask.of(-1, towardY, 0);
            blocks |= TransitionMask.of(1, towardY, 0);
        }

        return blocks;
    }
}
