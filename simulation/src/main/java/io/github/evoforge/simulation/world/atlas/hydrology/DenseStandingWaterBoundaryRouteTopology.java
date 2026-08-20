package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterBoundaryRouteTopology}. */
final class DenseStandingWaterBoundaryRouteTopology implements StandingWaterBoundaryRouteTopology {
    private final WorldBounds bounds;
    private final List<StandingWaterBoundaryRoute> routes;

    DenseStandingWaterBoundaryRouteTopology(
            WorldBounds bounds,
            List<StandingWaterBoundaryRoute> routes) {
        if (bounds == null || routes == null) {
            throw new IllegalArgumentException("standing-water route topology inputs must not be null");
        }
        this.routes = List.copyOf(routes);
        for (int bodyId = 0; bodyId < this.routes.size(); bodyId++) {
            StandingWaterBoundaryRoute route = this.routes.get(bodyId);
            if (route == null || route.bodyId() != bodyId) {
                throw new IllegalArgumentException("standing-water routes must be dense and ordered");
            }
        }
        this.bounds = bounds;
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return routes.size();
    }

    @Override
    public StandingWaterBoundaryRoute route(int bodyId) {
        if (bodyId < 0 || bodyId >= routes.size()) {
            throw new IllegalArgumentException("unknown standing-water body id: " + bodyId);
        }
        return routes.get(bodyId);
    }
}
