package io.github.evoforge.simulation.world.pathfinding;

/** Deterministically resumable path search measured in node expansions. */
public interface PathSearch {

    PathSearchStatus status();

    PathSearchStatus advance(int expansionBudget);

    /** Stops unfinished computational work and releases strategy-owned search state. */
    void cancel();

    PathRoute route();

    PathSearchMetrics metrics();
}
