package io.github.evoforge.simulation.world.pathfinding;

/** Deterministically resumable path search measured in node expansions. */
public interface PathSearch {

    PathSearchStatus status();

    PathSearchStatus advance(int expansionBudget);

    PathRoute route();

    PathSearchMetrics metrics();
}
