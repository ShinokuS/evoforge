package io.github.evoforge.simulation.mechanics.movement;

/** Synchronous continuation port for completed concrete movement edges. */
@FunctionalInterface
public interface MovementStepCompletionSink {

    void completed(MovementStepCompletion completion);
}
