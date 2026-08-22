package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.navigation.traversal.MoverDestinationAccessResolver;
import io.github.evoforge.simulation.world.navigation.pathfinding.Pathfinder;

/** Runtime movement capabilities shared with agents, commands and presentation. */
record MovementRuntime(
        Pathfinder pathfinder,
        MovementSystem movement,
        MoveToSystem moveTo,
        MoverDestinationAccessResolver destinationAccess) { }
