package io.github.evoforge.simulation.world.navigation.pathfinding;

/** Starts disposable read-only route searches. */
public interface Pathfinder {

    PathSearch begin(PathQuery query);
}
