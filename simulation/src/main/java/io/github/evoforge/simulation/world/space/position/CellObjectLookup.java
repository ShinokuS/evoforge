package io.github.evoforge.simulation.world.space.position;

import io.github.evoforge.simulation.world.object.ObjectId;

/**
 * Read-only lookup of objects occupying one discrete spatial cell.
 */
public interface CellObjectLookup {

    int objectCount(
            int x,
            int y,
            int z);

    ObjectId objectAt(
            int x,
            int y,
            int z,
            int index);
}
