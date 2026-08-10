package io.github.evoforge.simulation.world.mechanics.geometry;

public final class RampShape
        implements Shape {

    public static final RampShape POSITIVE_X =
            new RampShape(1, 0);
    public static final RampShape NEGATIVE_X =
            new RampShape(-1, 0);
    public static final RampShape POSITIVE_Y =
            new RampShape(0, 1);
    public static final RampShape NEGATIVE_Y =
            new RampShape(0, -1);

    private final int riseX;
    private final int riseY;

    private final long lowerPorts;
    private final long rampPorts;
    private final long upperPorts;

    private RampShape(
            int riseX,
            int riseY) {

        this.riseX = riseX;
        this.riseY = riseY;

        int lowerToRamp =
                TransitionMask.of(
                        riseX,
                        riseY,
                        1);

        int rampToLower =
                TransitionMask.of(
                        -riseX,
                        -riseY,
                        -1);

        int rampToUpper =
                TransitionMask.of(
                        riseX,
                        riseY,
                        0);

        int upperToRamp =
                TransitionMask.of(
                        -riseX,
                        -riseY,
                        0);

        lowerPorts =
                TransitionPorts.of(
                        lowerToRamp,
                        lowerToRamp);

        int rampTransitions =
                rampToLower | rampToUpper;

        rampPorts =
                TransitionPorts.of(
                        rampTransitions,
                        rampTransitions);

        upperPorts =
                TransitionPorts.of(
                        upperToRamp,
                        upperToRamp);
    }

    @Override
    public long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ) {

        if (relativeX == -riseX
                && relativeY == -riseY
                && relativeZ == 0) {
            return lowerPorts;
        }

        if (relativeX == 0
                && relativeY == 0
                && relativeZ == 1) {
            return rampPorts;
        }

        if (relativeX == riseX
                && relativeY == riseY
                && relativeZ == 1) {
            return upperPorts;
        }

        return TransitionPorts.NONE;
    }
}
