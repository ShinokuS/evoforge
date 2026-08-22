package io.github.evoforge.simulation.mechanics.movement;

/**
 * Bind-once bootstrap relay for the single Movement edge-completion consumer.
 * This is deliberately not a listener collection or event bus.
 */
public final class MovementStepCompletionRelay
        implements MovementStepCompletionSink {

    private MovementStepCompletionSink target;

    public void bind(
            MovementStepCompletionSink target) {

        if (target == null) {
            throw new IllegalArgumentException(
                    "target must not be null");
        }
        if (this.target != null) {
            throw new IllegalStateException(
                    "movement completion relay is already bound");
        }
        this.target = target;
    }

    @Override
    public void completed(
            MovementStepCompletion completion) {

        if (completion == null) {
            throw new IllegalArgumentException(
                    "completion must not be null");
        }
        if (target == null) {
            throw new IllegalStateException(
                    "movement completion relay is not bound");
        }
        target.completed(completion);
    }
}
