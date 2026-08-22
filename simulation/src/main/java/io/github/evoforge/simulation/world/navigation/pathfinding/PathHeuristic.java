package io.github.evoforge.simulation.world.navigation.pathfinding;

/** Lower-bound cost estimate used by an exact spatial search implementation. */
@FunctionalInterface
public interface PathHeuristic {

    long estimate(
            int x,
            int y,
            int z,
            int goalX,
            int goalY,
            int goalZ);
}
