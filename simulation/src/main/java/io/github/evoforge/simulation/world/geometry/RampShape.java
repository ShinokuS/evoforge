package io.github.evoforge.simulation.world.geometry;

import io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

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
    private final int sideX;
    private final int sideY;

    private final long lowerPorts;
    private final long rampPorts;
    private final long upperPorts;
    private final long higherPorts;
    private final long positiveSideArrivalPorts;
    private final long negativeSideArrivalPorts;

    private RampShape(
            int riseX,
            int riseY) {

        this.riseX = riseX;
        this.riseY = riseY;
        sideX = -riseY;
        sideY = riseX;

        int lowerToRamp = TransitionMask.of(riseX, riseY, 1);
        int rampToLower = TransitionMask.of(-riseX, -riseY, -1);
        int rampToUpper = TransitionMask.of(riseX, riseY, 0);
        int rampToRamp = TransitionMask.of(riseX, riseY, 1);
        int upperToRamp = TransitionMask.of(-riseX, -riseY, 0);
        int positiveSide = TransitionMask.of(sideX, sideY, 0);
        int negativeSide = TransitionMask.of(-sideX, -sideY, 0);

        lowerPorts = TransitionPorts.arrivalsOnly(lowerToRamp);
        rampPorts = TransitionPorts.departuresOnly(
                rampToLower
                        | rampToUpper
                        | rampToRamp
                        | positiveSide
                        | negativeSide);
        upperPorts = TransitionPorts.arrivalsOnly(upperToRamp);
        higherPorts = TransitionPorts.arrivalsOnly(rampToLower);
        positiveSideArrivalPorts = TransitionPorts.arrivalsOnly(positiveSide);
        negativeSideArrivalPorts = TransitionPorts.arrivalsOnly(negativeSide);
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
    public SurfaceBoundaryProfile surfaceBoundaryProfile(CellFace face) {
        if (face == null || face.dz() != 0) {
            throw new IllegalArgumentException("surface boundary requires a horizontal face");
        }
        if (face.dx() != 0) {
            return new SurfaceBoundaryProfile(
                    surfaceHeightAtCorner(face.dx(), -1),
                    surfaceHeightAtCorner(face.dx(), 1));
        }
        return new SurfaceBoundaryProfile(
                surfaceHeightAtCorner(-1, face.dy()),
                surfaceHeightAtCorner(1, face.dy()));
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

        if (relativeZ == 1
                && relativeX == -sideX
                && relativeY == -sideY) {
            return positiveSideArrivalPorts;
        }

        if (relativeZ == 1
                && relativeX == sideX
                && relativeY == sideY) {
            return negativeSideArrivalPorts;
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

    private int surfaceHeightAtCorner(int xSign, int ySign) {
        int along = riseX != 0 ? xSign * riseX : ySign * riseY;
        return along > 0 ? CellSpace.FULL_HEIGHT : CellSpace.EMPTY_HEIGHT;
    }
}
