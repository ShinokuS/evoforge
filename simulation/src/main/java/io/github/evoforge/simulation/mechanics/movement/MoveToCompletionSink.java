package io.github.evoforge.simulation.mechanics.movement;

/** Neutral terminal-completion sink for long-range MoveTo lifecycles. */
@FunctionalInterface
public interface MoveToCompletionSink {
    MoveToCompletionSink IGNORE = completion -> { };

    void completed(MoveToCompletion completion);
}
