package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterDomainTopology}. */
final class DenseStandingWaterDomainTopology implements StandingWaterDomainTopology {
    private final WorldBounds bounds;
    private final List<StandingWaterDomainRole> roles;

    DenseStandingWaterDomainTopology(WorldBounds bounds, List<StandingWaterDomainRole> roles) {
        if (bounds == null || roles == null || roles.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("standing-water domain inputs must not be null");
        }
        this.bounds = bounds;
        this.roles = List.copyOf(roles);
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return roles.size();
    }

    @Override
    public StandingWaterDomainRole role(int bodyId) {
        if (bodyId < 0 || bodyId >= roles.size()) {
            throw new IllegalArgumentException("unknown standing-water domain body id: " + bodyId);
        }
        return roles.get(bodyId);
    }
}
