package io.github.evoforge.simulation.world.navigation.pathfinding;

/** Algorithm-neutral counters for diagnostics and representative profiling. */
public record PathSearchMetrics(
        long expandedNodes,
        long generatedTransitions,
        long relaxedNodes,
        long reopenedNodes,
        int peakFrontier) {
}
