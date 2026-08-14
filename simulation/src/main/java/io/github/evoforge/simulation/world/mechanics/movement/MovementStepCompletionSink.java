package io.github.evoforge.simulation.world.mechanics.movement;

/** Synchronous continuation port for completed concrete movement edges. */
@FunctionalInterface
public interface MovementStepCompletionSink {

    void completed(MovementStepCompletion completion);
}
