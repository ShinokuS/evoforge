package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Optional;

/** Requested immutable specification for a generated world before generation begins. */
public record WorldSpec(
        WorldBounds bounds,
        ClimateSpec climate,
        Optional<PhysicalSpaceScale> physicalSpaceScale) {

    public WorldSpec(WorldBounds bounds) {
        this(bounds, ClimateSpec.STANDARD, Optional.empty());
    }

    public WorldSpec(WorldBounds bounds, ClimateSpec climate) {
        this(bounds, climate, Optional.empty());
    }

    public WorldSpec(
            WorldBounds bounds,
            ClimateSpec climate,
            PhysicalSpaceScale physicalSpaceScale) {
        this(bounds, climate, Optional.of(physicalSpaceScale));
    }

    public WorldSpec {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        if (climate == null) {
            throw new IllegalArgumentException("climate must not be null");
        }
        if (physicalSpaceScale == null) {
            throw new IllegalArgumentException("physical space scale optional must not be null");
        }
    }

    /** Requires physical world dimensions for a consumer that cannot operate in abstract cells. */
    public PhysicalSpaceScale requirePhysicalSpaceScale() {
        return physicalSpaceScale.orElseThrow(() -> new IllegalStateException(
                "physical space scale is not specified for this world"));
    }
}
