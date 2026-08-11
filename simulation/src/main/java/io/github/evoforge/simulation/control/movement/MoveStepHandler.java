package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.CommandHandler;
import io.github.evoforge.simulation.world.mechanics.movement.MovementStartResult;
import io.github.evoforge.simulation.world.mechanics.movement.MovementSystem;

public final class MoveStepHandler
        implements CommandHandler<
                MoveStepCommand,
                MoveStepResult> {

    private final MovementSystem movement;

    public MoveStepHandler(
            MovementSystem movement) {

        if (movement == null) {
            throw new IllegalArgumentException(
                    "movement must not be null");
        }

        this.movement = movement;
    }

    @Override
    public MoveStepResult handle(
            MoveStepCommand command) {

        if (command == null) {
            throw new IllegalArgumentException(
                    "command must not be null");
        }

        MovementStartResult result =
                movement.startStep(
                        command.objectId(),
                        command.x(),
                        command.y(),
                        command.z());

        return switch (result) {
            case STARTED -> MoveStepResult.STARTED;
            case MOVEMENT_UNAVAILABLE ->
                    MoveStepResult.MOVEMENT_UNAVAILABLE;
            case NOT_PLACED -> MoveStepResult.NOT_PLACED;
            case ALREADY_MOVING -> MoveStepResult.ALREADY_MOVING;
            case NOT_ADJACENT -> MoveStepResult.NOT_ADJACENT;
            case TRANSITION_UNAVAILABLE ->
                    MoveStepResult.TRANSITION_UNAVAILABLE;
        };
    }
}
