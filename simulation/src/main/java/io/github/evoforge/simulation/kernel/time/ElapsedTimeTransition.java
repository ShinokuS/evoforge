package io.github.evoforge.simulation.kernel.time;

/**
 * Replaceable rule for advancing one owner's state across an elapsed interval in one logical step.
 * Implementations may use a closed-form solution, a bounded event list, or their own adaptive solver.
 */
@FunctionalInterface
public interface ElapsedTimeTransition<S> {
    S advance(S currentState, SimulationInstant from, SimulationInstant to);
}
