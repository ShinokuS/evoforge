package io.github.evoforge.simulation.world.space.orientation;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import java.util.HashMap;
import java.util.Map;

/** Authoritative owner of mutable horizontal facing. */
public final class OrientationSystem implements OrientationLookup, OrientationMutations {

    private final ObjectLookup objects;
    private final Map<ObjectId, FacingDirection> facingByObject = new HashMap<>();

    public OrientationSystem(ObjectLookup objects) {
        if (objects == null) throw new IllegalArgumentException("objects must not be null");
        this.objects = objects;
    }

    public void attach(ObjectId objectId, FacingDirection facing) {
        if (!objects.isAlive(objectId)) throw new IllegalArgumentException("object must be alive: " + objectId);
        if (facing == null) throw new IllegalArgumentException("facing must not be null");
        if (facingByObject.putIfAbsent(objectId, facing) != null) {
            throw new IllegalStateException("orientation already attached: " + objectId);
        }
    }

    @Override
    public boolean has(ObjectId objectId) {
        return objectId != null && facingByObject.containsKey(objectId);
    }

    @Override
    public FacingDirection facing(ObjectId objectId) {
        FacingDirection facing = facingByObject.get(objectId);
        if (facing == null) throw new IllegalArgumentException("orientation not found: " + objectId);
        return facing;
    }

    @Override
    public void faceIfPresent(ObjectId objectId, int dx, int dy) {
        if ((dx == 0 && dy == 0) || !has(objectId)) return;
        facingByObject.put(objectId, FacingDirection.of(dx, dy));
    }

    public void detach(ObjectId objectId) {
        facingByObject.remove(objectId);
    }
}
