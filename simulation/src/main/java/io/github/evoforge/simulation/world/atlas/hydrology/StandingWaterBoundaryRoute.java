package io.github.evoforge.simulation.world.atlas.hydrology;

import java.util.OptionalInt;
import java.util.OptionalLong;

/** Minimum-barrier potential route from one standing-water body toward boundary-connected water. */
public record StandingWaterBoundaryRoute(
        int bodyId,
        boolean boundaryConnected,
        OptionalInt nextBodyId,
        OptionalLong minimumBarrierElevationSubunits) {

    public StandingWaterBoundaryRoute {
        if (bodyId < 0) throw new IllegalArgumentException("route body id must be non-negative");
        if (nextBodyId == null || minimumBarrierElevationSubunits == null) {
            throw new IllegalArgumentException("route optionals must not be null");
        }
        if (boundaryConnected) {
            if (nextBodyId.isPresent()) {
                throw new IllegalArgumentException("boundary-connected water is terminal, not routed onward");
            }
            if (minimumBarrierElevationSubunits.isEmpty()
                    || minimumBarrierElevationSubunits.getAsLong() != 0L) {
                throw new IllegalArgumentException("boundary-connected water has zero barrier to itself");
            }
        } else if (nextBodyId.isPresent() != minimumBarrierElevationSubunits.isPresent()) {
            throw new IllegalArgumentException("non-terminal route needs both next body and barrier, or neither");
        }
        if (minimumBarrierElevationSubunits.isPresent()
                && minimumBarrierElevationSubunits.getAsLong() < 0L) {
            throw new IllegalArgumentException("route barrier must be non-negative");
        }
    }

    public boolean reachesBoundaryWater() {
        return boundaryConnected || nextBodyId.isPresent();
    }
}
