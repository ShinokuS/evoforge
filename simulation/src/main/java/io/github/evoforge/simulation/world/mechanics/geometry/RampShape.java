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
    private final long higherPorts;

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

        int rampToRamp =
                TransitionMask.of(
                        riseX,
                        riseY,
                        1);

        int upperToRamp =
                TransitionMask.of(
                        -riseX,
                        -riseY,
                        0);

        lowerPorts =
                TransitionPorts.arrivalsOnly(
                        lowerToRamp);

        rampPorts =
                TransitionPorts.departuresOnly(
                        rampToLower
                                | rampToUpper
                                | rampToRamp);

        upperPorts =
                TransitionPorts.arrivalsOnly(
                        upperToRamp);

        higherPorts =
                TransitionPorts.arrivalsOnly(
                        rampToLower);
    }

    /** Cardinal horizontal direction in which this ramp rises. */
    public int riseX() {
        return riseX;
    }

    /** Cardinal horizontal direction in which this ramp rises. */
    public int riseY() {
        return riseY;
    }

    @Override
    public int solidVolume() {
        return CellVolume.FULL / 2;
    }

    /**
     * Free wedge volume below height h for a unit ramp whose solid surface rises
     * linearly from local height 0 to 1.
     */
    @Override
    public int freeVolumeBelow(
            int localHeight) {

        int height = CellSpace.requireHeight(localHeight);
        return (int) (((long) height * height)
                / (2L * CellSpace.FULL_HEIGHT));
    }

    @Override
    public int boundaryOpeningFloor(
            CellFace face) {

        if (face == null) {
            throw new IllegalArgumentException(
                    "face must not be null");
        }

        if (face == CellFace.POSITIVE_Z) {
            return CellSpace.FULL_HEIGHT;
        }
        if (face == CellFace.NEGATIVE_Z) {
            return CellSpace.CLOSED;
        }

        if (face.dx() == riseX
                && face.dy() == riseY) {
            return CellSpace.CLOSED;
        }

        return CellSpace.EMPTY_HEIGHT;
    }

    @Override
    public int minimumTraversalFactor() {
        return ShapeTraversalFactor.NEUTRAL;
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

        if (relativeX == riseX
                && relativeY == riseY
                && relativeZ == 2) {
            return higherPorts;
        }

        return TransitionPorts.NONE;
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
