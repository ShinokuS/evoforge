package io.github.evoforge.simulation.mechanics.movement.command;

import io.github.evoforge.simulation.kernel.command.Command;
import io.github.evoforge.simulation.world.object.ObjectId;

/** External intent to cancel one active long-range movement order. */
public record CancelMoveToCommand(
        ObjectId objectId)
        implements Command<CancelMoveToResult> {

    public CancelMoveToCommand {
        if (objectId == null) {
            throw new IllegalArgumentException("objectId must not be null");
        }
    }
}
