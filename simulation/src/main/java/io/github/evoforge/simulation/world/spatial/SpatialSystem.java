package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

public final class SpatialSystem {

    private final TransformState transforms = new TransformState();

    private final SpatialIndex[] indexes;

    public SpatialSystem(
            SpatialIndex... indexes) {

        if (indexes == null) {
            throw new IllegalArgumentException(
                    "indexes must not be null");
        }

        this.indexes = indexes.clone();

        for (SpatialIndex index : this.indexes) {
            if (index == null) {
                throw new IllegalArgumentException(
                        "index must not be null");
            }
        }
    }

    public TransformLookup transforms() {
        return transforms;
    }

    public void place(
            ObjectId id,
            double x,
            double y,
            double z) {

        transforms.add(
                id,
                x,
                y,
                z);

        for (SpatialIndex index : indexes) {
            index.add(
                    id,
                    x,
                    y,
                    z);
        }
    }

    public void move(
            ObjectId id,
            double x,
            double y,
            double z) {

        double oldX = transforms.x(id);

        double oldY = transforms.y(id);

        double oldZ = transforms.z(id);

        transforms.move(
                id,
                x,
                y,
                z);

        for (SpatialIndex index : indexes) {
            index.move(
                    id,
                    oldX,
                    oldY,
                    oldZ,
                    x,
                    y,
                    z);
        }
    }

    public void remove(
            ObjectId id) {

        double x = transforms.x(id);

        double y = transforms.y(id);

        double z = transforms.z(id);

        transforms.remove(id);

        for (SpatialIndex index : indexes) {
            index.remove(
                    id,
                    x,
                    y,
                    z);
        }
    }
}