package io.github.evoforge.simulation.world.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCost;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.pathfinding.PathSearchMetrics;
import io.github.evoforge.simulation.world.pathfinding.PathSearchStatus;
import io.github.evoforge.simulation.world.pathfinding.PathTransitionConstraint;
import io.github.evoforge.simulation.world.pathfinding.Pathfinder;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;

final class MoveToQueryConstraintProviderTest {

    @Test
    void moveToAppliesProviderWithoutOwningItsDomain() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(
                        ObjectDefinitionId::of,
                        ObjectDefinitionId::asInt);
        ObjectDefinitionId walker =
                objectDefinitions.register("test:walker");
        objectDefinitions.freeze();

        ObjectRepository objects = new ObjectRepository();
        ObjectId objectId = new ObjectFactory(objects, objectDefinitions)
                .create(walker)
                .id();
        CellSpatialIndex cells = new CellSpatialIndex();
        SpatialSystem spatial = new SpatialSystem(cells);
        spatial.place(objectId, 0, 0, 0);

        OccupancyDefinitions occupancyDefinitions = new OccupancyDefinitions();
        occupancyDefinitions.freeze();
        OccupancySystem occupancy = new OccupancySystem(
                objects,
                cells.lookup(),
                occupancyDefinitions);

        MovementDefinitions movementDefinitions = new MovementDefinitions();
        movementDefinitions.put(walker, MovementRate.of(100));
        movementDefinitions.freeze();
        MovementStateStore movementState = new MovementStateStore();
        ProcessScheduler unusedScheduler = (delay, processId) -> { };
        MovementSystem movement = new MovementSystem(
                objects,
                spatial.transforms(),
                (x, y, z) -> 0L,
                movementDefinitions,
                (fromX, fromY, fromZ, toX, toY, toZ) -> TransitionCost.of(1),
                occupancy,
                movementState,
                unusedScheduler);

        PathTransitionConstraint caller =
                (fromX, fromY, fromZ, toX, toY, toZ) -> true;
        PathTransitionConstraint provided =
                (fromX, fromY, fromZ, toX, toY, toZ) -> false;
        ObjectId[] moverSeen = new ObjectId[1];
        PathTransitionConstraint[] callerSeen = new PathTransitionConstraint[1];
        MoveToQueryConstraintProvider provider = (moverId, callerConstraint) -> {
            moverSeen[0] = moverId;
            callerSeen[0] = callerConstraint;
            return provided;
        };

        PathTransitionConstraint[] pathConstraintSeen =
                new PathTransitionConstraint[1];
        Pathfinder pathfinder = query -> {
            pathConstraintSeen[0] = query.constraint();
            return noPathSearch();
        };
        MoveToSystem moveTo = new MoveToSystem(
                spatial.transforms(),
                pathfinder,
                movement,
                provider);

        moveTo.start(objectId, 2, 0, 0, caller);

        assertSame(objectId, moverSeen[0]);
        assertSame(caller, callerSeen[0]);
        assertSame(provided, pathConstraintSeen[0]);
        assertFalse(moveTo.isActive(objectId));
    }

    private static PathSearch noPathSearch() {
        return new PathSearch() {
            @Override
            public PathSearchStatus status() {
                return PathSearchStatus.NO_PATH;
            }

            @Override
            public PathSearchStatus advance(int expansionBudget) {
                return PathSearchStatus.NO_PATH;
            }

            @Override
            public void cancel() {
            }

            @Override
            public PathRoute route() {
                throw new IllegalStateException("no route");
            }

            @Override
            public PathSearchMetrics metrics() {
                return new PathSearchMetrics(0L, 0L, 0L, 0L, 0);
            }
        };
    }
}
