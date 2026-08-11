package io.github.evoforge.simulation.world.mechanics.geometry;

public final class FullShape
        implements Shape {

    public static final FullShape INSTANCE =
            new FullShape();

    private static final int HORIZONTAL =
            SolidCellBlocking.HORIZONTAL;

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

        return SolidCellBlocking.transitionBlocks(
                relativeX,
                relativeY,
                relativeZ);
    }
}
