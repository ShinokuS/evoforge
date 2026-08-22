package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;

public final class PositionSystem implements PositionMutations {

    private final PositionState positions = new PositionState();

    private final ObjectPositionIndex[] indexes;

    private boolean faulted;

    public PositionSystem(
            ObjectPositionIndex... indexes) {

        if (indexes == null) {
            throw new IllegalArgumentException(
                    "indexes must not be null");
        }

        this.indexes = indexes.clone();

        for (ObjectPositionIndex index : this.indexes) {
            if (index == null) {
                throw new IllegalArgumentException(
                        "index must not be null");
            }
        }
    }

    public PositionLookup positions() {
        return positions;
    }

    public boolean isFaulted() {
        return faulted;
    }

    @Override
    public void place(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireHealthy();

        positions.add(
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

    @Override
    public void move(
            ObjectId id,
            int x,
            int y,
            int z) {

        requireHealthy();

        int oldX = positions.x(id);

        int oldY = positions.y(id);

        int oldZ = positions.z(id);

        positions.move(
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

    @Override
    public void remove(
            ObjectId id) {

        requireHealthy();

        int x = positions.x(id);

        int y = positions.y(id);

        int z = positions.z(id);

        positions.remove(id);

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
            positions.remove(id);
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
            positions.move(
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
            positions.add(
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