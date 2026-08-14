package io.github.evoforge.simulation.world.agent.perception.vision;

/** Immutable parameters of one visual sense. */
public record VisionDefinition(int range, int horizontalFovDegrees) {
    public VisionDefinition {
        if (range < 0) throw new IllegalArgumentException("vision range must be >= 0");
        if (horizontalFovDegrees <= 0 || horizontalFovDegrees > 360) {
            throw new IllegalArgumentException("horizontal FOV must be in 1..360 degrees");
        }
    }
}
