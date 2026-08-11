package io.github.evoforge.simulation.world.mechanics.geometry;

public final class FullShape
        implements Shape {

    public static final FullShape INSTANCE =
            new FullShape();

    private static final int HORIZONTAL =
            SolidCellBlocking.HORIZONTAL;

    private static final int CARDINAL_UP =
            TransitionMask.of(-1, 0, 1)
                    | TransitionMask.of(1, 0, 1)
                    | TransitionMask.of(0, -1, 1)
                    | TransitionMask.of(0, 1, 1);

    private static final long TOP_PORTS =
            TransitionPorts.departuresOnly(
                    HORIZONTAL | CARDINAL_UP);

    private FullShape() {
    }

    @Override
    public long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeZ == 2
                && Math.abs(relativeX) + Math.abs(relativeY) == 1) {
            return TransitionPorts.arrivalsOnly(
                    TransitionMask.of(
                            -relativeX,
                            -relativeY,
                            -1));
        }

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

        return SolidCellBlocking.transitionBlocks(
                relativeX,
                relativeY,
                relativeZ);
    }
}
