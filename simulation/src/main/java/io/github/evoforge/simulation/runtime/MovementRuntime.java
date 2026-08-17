package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.world.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverDestinationAccessResolver;
import io.github.evoforge.simulation.world.pathfinding.Pathfinder;

/** Runtime movement capabilities shared with agents, commands and presentation. */
record MovementRuntime(
        Pathfinder pathfinder,
        MovementSystem movement,
        MoveToSystem moveTo,
        MoverDestinationAccessResolver destinationAccess) { }
