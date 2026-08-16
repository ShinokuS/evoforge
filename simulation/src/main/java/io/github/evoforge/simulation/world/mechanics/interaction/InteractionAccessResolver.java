package io.github.evoforge.simulation.world.mechanics.interaction;

import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometryLookup;

/** Resolves whether one standing cell can physically reach one target cell under a declarative profile. */
public final class InteractionAccessResolver {
    private final GeometryLookup geometry;

    public InteractionAccessResolver(GeometryLookup geometry) {
        if (geometry == null) throw new IllegalArgumentException("geometry must not be null");
        this.geometry = geometry;
    }

    public boolean allows(
            int siteX,
            int siteY,
            int siteZ,
            int targetX,
            int targetY,
            int targetZ,
            InteractionReachProfile profile) {
        if (profile == null) throw new IllegalArgumentException("profile must not be null");
        int dx = targetX - siteX;
        int dy = targetY - siteY;
        int dz = targetZ - siteZ;
        for (int index = 0; index < profile.count(); index++) {
            InteractionReachPattern pattern = profile.patternAt(index);
            CellOffset target = pattern.targetOffset();
            if (target.x() != dx || target.y() != dy || target.z() != dz) continue;
            for (CellOffset clearance : pattern.requiredOpenCells()) {
                if (CellSpace.capacity(geometry.find(
                        siteX + clearance.x(),
                        siteY + clearance.y(),
                        siteZ + clearance.z())) == 0) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
