package io.github.evoforge.simulation.agents.perception.vision;

import io.github.evoforge.simulation.agents.perception.PerceivedCell;
import io.github.evoforge.simulation.agents.perception.PerceivedObject;
import io.github.evoforge.simulation.agents.perception.PerceptionLookup;
import io.github.evoforge.simulation.agents.perception.PerceptionSnapshot;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.space.position.CellObjectLookup;
import io.github.evoforge.simulation.world.spatial.TransformLookup;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import io.github.evoforge.simulation.world.space.orientation.OrientationLookup;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Authoritative current-sight calculation. It owns visual rules, not memory or beliefs. */
public final class VisionSystem implements VisionLookup, PerceptionLookup {
    private final ObjectLookup objects;
    private final TransformLookup transforms;
    private final CellObjectLookup cells;
    private final OrientationLookup orientations;
    private final VisionDefinitions definitions;
    private final SightOcclusionLookup occlusion;

    public VisionSystem(ObjectLookup objects, TransformLookup transforms, CellObjectLookup cells,
            OrientationLookup orientations, VisionDefinitions definitions, SightOcclusionLookup occlusion) {
        if (objects == null || transforms == null || cells == null || orientations == null
                || definitions == null || occlusion == null) {
            throw new IllegalArgumentException("vision dependencies must not be null");
        }
        this.objects = objects;
        this.transforms = transforms;
        this.cells = cells;
        this.orientations = orientations;
        this.definitions = definitions;
        this.occlusion = occlusion;
    }

    @Override
    public PerceptionSnapshot perceive(ObjectId observerId) {
        VisionSnapshot vision = snapshot(observerId);
        if (vision == null) return PerceptionSnapshot.empty(observerId);
        List<PerceivedCell> perceivedCells = new ArrayList<>(vision.cells().size());
        for (VisibleCell cell : vision.cells()) {
            int distance = Math.max(
                    Math.max(Math.abs(cell.x() - vision.originX()), Math.abs(cell.y() - vision.originY())),
                    Math.abs(cell.z() - vision.originZ()));
            perceivedCells.add(new PerceivedCell(cell.x(), cell.y(), cell.z(), distance));
        }
        List<PerceivedObject> perceivedObjects = new ArrayList<>(vision.objects().size());
        for (VisibleObject object : vision.objects()) {
            perceivedObjects.add(new PerceivedObject(
                    object.objectId(), object.x(), object.y(), object.z(), object.distance()));
        }
        return new PerceptionSnapshot(observerId, perceivedCells, perceivedObjects);
    }

    @Override
    public VisionSnapshot snapshot(ObjectId observerId) {
        WorldObject observer = objects.get(observerId);
        if (observer == null || !definitions.has(observer.definitionId())) return null;
        if (!transforms.has(observerId)) throw new IllegalStateException("vision object has no transform: " + observerId);
        if (!orientations.has(observerId)) throw new IllegalStateException("vision object has no orientation: " + observerId);

        VisionDefinition definition = definitions.get(observer.definitionId());
        FacingDirection facing = orientations.facing(observerId);
        int ox = transforms.x(observerId);
        int oy = transforms.y(observerId);
        int oz = transforms.z(observerId);
        int range = definition.range();
        long rangeSquared = (long) range * range;
        double facingLength = StrictMath.sqrt((double) facing.x() * facing.x() + (double) facing.y() * facing.y());
        double minimumCosine = StrictMath.cos(StrictMath.toRadians(definition.horizontalFovDegrees() / 2.0));

        List<VisibleCell> visibleCells = new ArrayList<>();
        List<VisibleObject> visibleObjects = new ArrayList<>();
        Set<ObjectId> seenObjects = new HashSet<>();
        for (int z = oz - range; z <= oz + range; z++) {
            for (int y = oy - range; y <= oy + range; y++) {
                for (int x = ox - range; x <= ox + range; x++) {
                    int dx = x - ox;
                    int dy = y - oy;
                    int dz = z - oz;
                    long distanceSquared = (long) dx * dx + (long) dy * dy + (long) dz * dz;
                    if (distanceSquared > rangeSquared || !insideHorizontalFov(dx, dy, facing, facingLength, minimumCosine)) continue;
                    if (!lineClear(ox, oy, oz, x, y, z)) continue;
                    visibleCells.add(new VisibleCell(x, y, z));
                    int objectCount = cells.objectCount(x, y, z);
                    for (int index = 0; index < objectCount; index++) {
                        ObjectId objectId = cells.objectAt(x, y, z, index);
                        if (objectId == null || objectId.equals(observerId) || !seenObjects.add(objectId)) continue;
                        visibleObjects.add(new VisibleObject(objectId, x, y, z,
                                Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz))));
                    }
                }
            }
        }
        visibleObjects.sort(Comparator.comparingLong(value -> value.objectId().asLong()));
        return new VisionSnapshot(observerId, ox, oy, oz, facing, range,
                definition.horizontalFovDegrees(), visibleCells, visibleObjects);
    }

    private static boolean insideHorizontalFov(int dx, int dy, FacingDirection facing,
            double facingLength, double minimumCosine) {
        if (dx == 0 && dy == 0) return true;
        double targetLength = StrictMath.sqrt((double) dx * dx + (double) dy * dy);
        double cosine = ((double) facing.x() * dx + (double) facing.y() * dy) / (facingLength * targetLength);
        return cosine + 1.0e-12 >= minimumCosine;
    }

    private boolean lineClear(int ox, int oy, int oz, int tx, int ty, int tz) {
        int dx = tx - ox;
        int dy = ty - oy;
        int dz = tz - oz;
        int steps = Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz));
        if (steps <= 1) return true;
        int previousX = ox;
        int previousY = oy;
        int previousZ = oz;
        for (int step = 1; step < steps; step++) {
            int x = ox + (int) StrictMath.round((double) dx * step / steps);
            int y = oy + (int) StrictMath.round((double) dy * step / steps);
            int z = oz + (int) StrictMath.round((double) dz * step / steps);
            if (x == previousX && y == previousY && z == previousZ) continue;
            previousX = x;
            previousY = y;
            previousZ = z;
            if (occlusion.blocksSight(x, y, z)) return false;
        }
        return true;
    }
}
