package io.github.evoforge.simulation.world.spatial;

import io.github.evoforge.simulation.world.object.ObjectId;

public final class SpatialSystem {

    private final TransformState transforms = new TransformState();

    private final ObjectSpatialIndex[] indexes;

    private boolean faulted;

    public SpatialSystem(
            ObjectSpatialIndex... indexes) {

        if (indexes == null) {
            throw new IllegalArgumentException(
                    "indexes must not be null");
        }

        this.indexes = indexes.clone();

        for (ObjectSpatialIndex index : this.indexes) {
            if (index == null) {
                throw new IllegalArgumentException(
                        "index must not be null");
            }
        }
    }

    public TransformLookup transforms() {
        return transforms;
    }

    public boolean isFaulted() {
        return faulted;
    }

    public void place(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireHealthy();

        transforms.add(
                id,
                x,
                y,
                z);

        for (int i = 0; i < indexes.length; i++) {

            try {
                indexes[i].add(
                        id,
                        x,
                        y,
                        z);
            } catch (RuntimeException failure) {
                faulted = true;

                rollbackPlace(
                        i,
                        id,
                        x,
                        y,
                        z,
                        failure);

                throw failure;
            }
        }
    }

    public void move(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireHealthy();

        int oldX = transforms.x(id);

        int oldY = transforms.y(id);

        int oldZ = transforms.z(id);

        transforms.move(
                id,
                x,
                y,
                z);

        for (int i = 0; i < indexes.length; i++) {

            try {
                indexes[i].move(
                        id,
                        oldX,
                        oldY,
                        oldZ,
                        x,
                        y,
                        z);
            } catch (RuntimeException failure) {
                faulted = true;

                rollbackMove(
                        i,
                        id,
                        oldX,
                        oldY,
                        oldZ,
                        x,
                        y,
                        z,
                        failure);

                throw failure;
            }
        }
    }

    public void remove(
            ObjectId id) {

        requireHealthy();

        int x = transforms.x(id);

        int y = transforms.y(id);

        int z = transforms.z(id);

        transforms.remove(id);

        for (int i = 0; i < indexes.length; i++) {

            try {
                indexes[i].remove(
                        id,
                        x,
                        y,
                        z);
            } catch (RuntimeException failure) {
                faulted = true;

                rollbackRemove(
                        i,
                        id,
                        x,
                        y,
                        z,
                        failure);

                throw failure;
            }
        }
    }

    private void rollbackPlace(
            int completedIndexes,
            ObjectId id,
            int x,
            int y,
            int z,
            RuntimeException failure) {

        for (int i = completedIndexes - 1; i >= 0; i--) {

            try {
                indexes[i].remove(
                        id,
                        x,
                        y,
                        z);
            } catch (RuntimeException rollbackFailure) {
                addSuppressed(
                        failure,
                        rollbackFailure);
            }
        }

        try {
            transforms.remove(id);
        } catch (RuntimeException rollbackFailure) {
            addSuppressed(
                    failure,
                    rollbackFailure);
        }
    }

    private void rollbackMove(
            int completedIndexes,
            ObjectId id,
            int oldX,
            int oldY,
            int oldZ,
            int newX,
            int newY,
            int newZ,
            RuntimeException failure) {

        for (int i = completedIndexes - 1; i >= 0; i--) {

            try {
                indexes[i].move(
                        id,
                        newX,
                        newY,
                        newZ,
                        oldX,
                        oldY,
                        oldZ);
            } catch (RuntimeException rollbackFailure) {
                addSuppressed(
                        failure,
                        rollbackFailure);
            }
        }

        try {
            transforms.move(
                    id,
                    oldX,
                    oldY,
                    oldZ);
        } catch (RuntimeException rollbackFailure) {
            addSuppressed(
                    failure,
                    rollbackFailure);
        }
    }

    private void rollbackRemove(
            int completedIndexes,
            ObjectId id,
            int x,
            int y,
            int z,
            RuntimeException failure) {

        for (int i = completedIndexes - 1; i >= 0; i--) {

            try {
                indexes[i].add(
                        id,
                        x,
                        y,
                        z);
            } catch (RuntimeException rollbackFailure) {
                addSuppressed(
                        failure,
                        rollbackFailure);
            }
        }

        try {
            transforms.add(
                    id,
                    x,
                    y,
                    z);
        } catch (RuntimeException rollbackFailure) {
            addSuppressed(
                    failure,
                    rollbackFailure);
        }
    }

    private void requireHealthy() {
        if (faulted) {
            throw new IllegalStateException(
                    "spatial system is faulted");
        }
    }

    private static void addSuppressed(
            RuntimeException failure,
            RuntimeException rollbackFailure) {

        if (failure != rollbackFailure) {
            failure.addSuppressed(
                    rollbackFailure);
        }
    }
}