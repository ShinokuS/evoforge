package io.github.evoforge.simulation.world.geometry;

import io.github.evoforge.simulation.world.mechanics.geometry.ShapeTraversalFactor;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;

public interface Shape {

    long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ);

    /**
     * Approximate solid volume occupied by this terrain Shape inside its anchor cell.
     *
     * <p>The value uses the material-agnostic {@link CellVolume} fixed-point scale.
     * It describes occupied volume only; it does not define free-space connectivity,
     * hydraulic behavior or traversal topology.
     */
    default int solidVolume() {
        return CellVolume.FULL;
    }

    /**
     * Approximate free geometric volume below a normalized local height.
     *
     * <p>The default preserves lightweight Shape implementations by distributing the
     * free volume implied by {@link #solidVolume()} uniformly over height. Shapes with
     * meaningful internal geometry should override this objective physical profile.
     * Consumers must not infer traversal roles from it.
     */
    default int freeVolumeBelow(
            int localHeight) {

        int height = CellSpace.requireHeight(localHeight);
        int freeCapacity = CellVolume.FULL
                - CellVolume.requireValid(solidVolume());

        return (int) (((long) height * freeCapacity)
                / CellSpace.FULL_HEIGHT);
    }

    /**
     * Lowest local elevation at which this Shape's free space opens through a cell
     * face, or {@link CellSpace#CLOSED} if the face is physically closed.
     *
     * <p>The default is conservative: an unknown Shape does not expose cross-cell
     * physical connectivity merely because it has spare volume. This contract is
     * independent from navigation {@link #transitionPorts(int, int, int)}.
     */
    default int boundaryOpeningFloor(
            CellFace face) {

        if (face == null) {
            throw new IllegalArgumentException(
                    "face must not be null");
        }
        return CellSpace.CLOSED;
    }

    /**
     * Objective top-surface profile along one horizontal face of the anchor cell.
     *
     * <p>Consumers compare these profiles to decide whether adjacent terrain surfaces
     * are geometrically continuous. The default matches the interface's default full
     * solid cell. Shape identity is intentionally absent from this contract.</p>
     */
    default SurfaceBoundaryProfile surfaceBoundaryProfile(
            CellFace face) {
        if (face == null || face.dz() != 0) {
            throw new IllegalArgumentException("surface boundary requires a horizontal face");
        }
        return SurfaceBoundaryProfile.flat(CellSpace.FULL_HEIGHT);
    }

    default int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {

        return TransitionMask.NONE;
    }

    /**
     * Guaranteed lower bound for every positive traversal factor this Shape may return.
     * Implementations with factors below NEUTRAL must override this conservatively.
     */
    default int minimumTraversalFactor() {
        return 1;
    }

    default int departureTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {

        int direction = TransitionMask.of(
                directionX,
                directionY,
                directionZ);

        int departures = TransitionPorts.departures(
                transitionPorts(
                        relativeX,
                        relativeY,
                        relativeZ));

        return (departures & direction) != 0
                ? ShapeTraversalFactor.NEUTRAL
                : ShapeTraversalFactor.NONE;
    }

    default int arrivalTraversalFactor(
            int relativeX,
            int relativeY,
            int relativeZ,
            int directionX,
            int directionY,
            int directionZ) {

        int direction = TransitionMask.of(
                directionX,
                directionY,
                directionZ);

        int arrivals = TransitionPorts.arrivals(
                transitionPorts(
                        relativeX,
                        relativeY,
                        relativeZ));

        return (arrivals & direction) != 0
                ? ShapeTraversalFactor.NEUTRAL
                : ShapeTraversalFactor.NONE;
    }
}
