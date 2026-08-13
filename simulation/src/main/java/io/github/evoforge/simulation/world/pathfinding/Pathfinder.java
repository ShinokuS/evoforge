package io.github.evoforge.simulation.world.pathfinding;

/** Starts disposable read-only route searches. */
public interface Pathfinder {

    PathSearch begin(PathQuery query);
}
