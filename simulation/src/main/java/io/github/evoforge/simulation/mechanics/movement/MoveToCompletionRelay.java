package io.github.evoforge.simulation.mechanics.movement;

/**
 * Bind-once bootstrap relay for the autonomous MoveTo completion consumer.
 * This deliberately avoids coupling MoveToSystem to AgentSystem or introducing an event bus.
 */
public final class MoveToCompletionRelay implements MoveToCompletionSink {
    private MoveToCompletionSink target;

    public void bind(MoveToCompletionSink target) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        if (this.target != null) throw new IllegalStateException("MoveTo completion relay is already bound");
        this.target = target;
    }

    @Override
    public void completed(MoveToCompletion completion) {
        if (completion == null) throw new IllegalArgumentException("completion must not be null");
        if (target != null) target.completed(completion);
    }
}
