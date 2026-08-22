package io.github.evoforge.simulation.world.navigation.pathfinding;

public enum PathSearchStatus {
    RUNNING,
    FOUND,
    NO_PATH,
    STALE,
    CANCELLED
}
