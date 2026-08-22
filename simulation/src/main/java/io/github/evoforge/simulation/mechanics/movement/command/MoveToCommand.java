package io.github.evoforge.simulation.mechanics.movement.command;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.world.object.ObjectId;

/** External intent to move one object to a long-range world coordinate. */
public record MoveToCommand(
        ObjectId objectId,
        int x,
        int y,
        int z)
        implements Command<MoveToResult> {

    public MoveToCommand {
        if (objectId == null) {
            throw new IllegalArgumentException(
                    "objectId must not be null");
        }
    }
}
