package io.github.evoforge.simulation.world.interaction;

import java.util.List;

/** One allowed target offset plus cells that must remain physically open for the reach. */
public record InteractionReachPattern(
        CellOffset targetOffset,
        List<CellOffset> requiredOpenCells) {

    public InteractionReachPattern {
        if (targetOffset == null || requiredOpenCells == null) {
            throw new IllegalArgumentException("reach pattern values must not be null");
        }
        requiredOpenCells = List.copyOf(requiredOpenCells);
        for (CellOffset offset : requiredOpenCells) {
            if (offset == null) throw new IllegalArgumentException("required open offset must not be null");
        }
    }

    public static InteractionReachPattern direct(CellOffset targetOffset) {
        return new InteractionReachPattern(targetOffset, List.of());
    }
}
