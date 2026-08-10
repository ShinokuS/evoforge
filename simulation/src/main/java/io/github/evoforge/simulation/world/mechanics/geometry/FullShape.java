package io.github.evoforge.simulation.world.mechanics.geometry;

public final class FullShape
        implements Shape {

    public static final FullShape INSTANCE =
            new FullShape();

    private static final int TOP_TRANSITIONS =
            TransitionMask.of(-1, -1, 0)
                    | TransitionMask.of(0, -1, 0)
                    | TransitionMask.of(1, -1, 0)
                    | TransitionMask.of(-1, 0, 0)
                    | TransitionMask.of(1, 0, 0)
                    | TransitionMask.of(-1, 1, 0)
                    | TransitionMask.of(0, 1, 0)
                    | TransitionMask.of(1, 1, 0);

    private FullShape() {
    }

    @Override
    public int transitionMask(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeZ != 1
                || relativeX < -1 || relativeX > 1
                || relativeY < -1 || relativeY > 1) {
            return TransitionMask.NONE;
        }

        if (relativeX == 0 && relativeY == 0) {
            return TOP_TRANSITIONS;
        }

        return TransitionMask.of(
                -relativeX,
                -relativeY,
                0);
    }
}
