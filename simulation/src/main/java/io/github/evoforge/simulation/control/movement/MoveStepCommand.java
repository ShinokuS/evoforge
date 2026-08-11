package io.github.evoforge.simulation.control.movement;

import io.github.evoforge.simulation.control.core.Command;
import io.github.evoforge.simulation.world.object.ObjectId;

public record MoveStepCommand(
        ObjectId objectId,
        int x,
        int y,
        int z)
        implements Command<MoveStepResult> {

    public MoveStepCommand {
        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
    }
}
