package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.kernel.scheduling.BoundProcessScheduler;
import io.github.evoforge.simulation.kernel.scheduling.HandlerId;
import io.github.evoforge.simulation.kernel.scheduling.ProcessScheduler;
import io.github.evoforge.simulation.mechanics.movement.MoveToSystem;
import io.github.evoforge.simulation.mechanics.movement.MovementActionProcessor;
import io.github.evoforge.simulation.mechanics.movement.MovementStepCompletionRelay;
import io.github.evoforge.simulation.mechanics.movement.MovementSystem;
import io.github.evoforge.simulation.world.navigation.traversal.MoverDestinationAccessResolver;
import io.github.evoforge.simulation.mechanics.movement.MoverTraversalQueryConstraintProvider;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCostCalculator;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCostLowerBoundCalculator;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCostLowerBoundLookup;
import io.github.evoforge.simulation.world.navigation.traversal.water.WaterWadingConstraint;
import io.github.evoforge.simulation.world.navigation.pathfinding.ExactAStarPathfinder;
import io.github.evoforge.simulation.world.navigation.pathfinding.HierarchicalPathfinder;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathHeuristics;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathHierarchyConfig;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathHierarchyIndex;
import io.github.evoforge.simulation.world.navigation.pathfinding.Pathfinder;

/** Builds timed movement, traversal constraints and long-range pathfinding. */
final class MovementRuntimeAssembly {
    private MovementRuntimeAssembly() { }

    static MovementRuntime assemble(
            SimulationDefinitions definitions,
            SimulationWorldState world,
            RuntimeKernel kernel) {
        WaterWadingConstraint waterWading = new WaterWadingConstraint(
                world.objects,
                definitions.waterWading,
                world.water.lookup(),
                world.geometry);

        MovementStepCompletionRelay movementCompletions = new MovementStepCompletionRelay();
        MovementActionProcessor movementActions = new MovementActionProcessor(
                world.movementState,
                world.objects,
                world.spatial.positions(),
                world.navigation.lookup(),
                waterWading,
                world.occupancy,
                world.spatial,
                world.orientations,
                movementCompletions);
        HandlerId movementHandlerId = kernel.handlers.register(movementActions::complete);
        ProcessScheduler movementScheduler = new BoundProcessScheduler(
                kernel.clock,
                kernel.scheduler,
                movementHandlerId);

        TransitionCostCalculator transitionCosts = new TransitionCostCalculator(
                world.landscape.terrain(),
                world.geometry,
                definitions.landscapeTraversal);
        TransitionCostLowerBoundLookup transitionCostBounds = new TransitionCostLowerBoundCalculator(
                definitions.landscapeTraversal,
                world.landscape.shapeTraversalBounds());
        ExactAStarPathfinder exactPathfinder = new ExactAStarPathfinder(
                world.navigation.lookup(),
                transitionCosts,
                world.landscape.traversalRevision(),
                PathHeuristics.chebyshev(transitionCostBounds));
        PathHierarchyIndex hierarchy = new PathHierarchyIndex(
                world.navigation.lookup(),
                world.landscape.traversalChanges(),
                PathHierarchyConfig.standard());
        Pathfinder pathfinder = new HierarchicalPathfinder(hierarchy, exactPathfinder);
        MovementSystem movement = new MovementSystem(
                world.objects,
                world.spatial.positions(),
                world.navigation.lookup(),
                definitions.movement,
                transitionCosts,
                waterWading,
                world.occupancy,
                world.movementState,
                movementScheduler);
        MoveToSystem moveTo = new MoveToSystem(
                world.spatial.positions(),
                pathfinder,
                movement,
                new MoverTraversalQueryConstraintProvider(waterWading));
        movementCompletions.bind(moveTo);
        MoverDestinationAccessResolver destinationAccess = new MoverDestinationAccessResolver(
                world.navigation.lookup(),
                waterWading);

        return new MovementRuntime(pathfinder, movement, moveTo, destinationAccess);
    }
}
