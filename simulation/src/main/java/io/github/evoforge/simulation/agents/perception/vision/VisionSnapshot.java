package io.github.evoforge.simulation.agents.perception.vision;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import java.util.List;

/** Immutable result of applying the authoritative visual-sense rules at one instant. */
public record VisionSnapshot(
        ObjectId observerId,
        int originX,
        int originY,
        int originZ,
        FacingDirection facing,
        int range,
        int horizontalFovDegrees,
        List<VisibleCell> cells,
        List<VisibleObject> objects) {

    public VisionSnapshot {
        if (observerId == null || facing == null || cells == null || objects == null) {
            throw new IllegalArgumentException("vision snapshot values must not be null");
        }
        cells = List.copyOf(cells);
        objects = List.copyOf(objects);
    }

    public boolean isCellVisible(int x, int y, int z) {
        for (VisibleCell cell : cells) if (cell.x() == x && cell.y() == y && cell.z() == z) return true;
        return false;
    }

    public boolean isObjectVisible(ObjectId objectId) {
        if (objectId == null) return false;
        for (VisibleObject object : objects) if (object.objectId().equals(objectId)) return true;
        return false;
    }
}
