package io.github.evoforge.simulation.world.pathfinding;

/** Deterministically resumable path search measured in node expansions. */
public interface PathSearch {

    PathSearchStatus status();

    PathSearchStatus advance(int expansionBudget);

    /** Stops an unfinished computational search and releases reusable workspace. */
    void cancel();

    PathRoute route();

    PathSearchMetrics metrics();
}
