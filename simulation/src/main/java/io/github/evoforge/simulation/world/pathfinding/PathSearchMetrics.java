package io.github.evoforge.simulation.world.pathfinding;

/** Algorithm-neutral counters for diagnostics and representative profiling. */
public record PathSearchMetrics(
        long expandedNodes,
        long generatedTransitions,
        long relaxedNodes,
        long reopenedNodes,
        int peakFrontier) {
}
