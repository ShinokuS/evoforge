package io.github.evoforge.simulation.world.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.time.ProcessScheduler;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverTraversalConstraint;
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

final class MoveToMoverConstraintTest {

    @Test
    void planningComposesMoverRestrictionAndPreservesCallerRevision() {
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

        MoverTraversalConstraint moverConstraint =
                (moverId, fromX, fromY, fromZ, toX, toY, toZ) -> false;
        MovementSystem movement = new MovementSystem(
                objects,
                spatial.transforms(),
                (x, y, z) -> 0,
                movementDefinitions,
                (fromX, fromY, fromZ, toX, toY, toZ) -> TransitionCost.of(1),
                moverConstraint,
                occupancy,
                movementState,
                unusedScheduler);

        boolean[] allowedSeen = new boolean[1];
        long[] revisionSeen = new long[1];
        Pathfinder pathfinder = query -> {
            allowedSeen[0] = query.constraint().allows(
                    0, 0, 0, 1, 0, 0);
            revisionSeen[0] = query.constraint().revision();
            return noPathSearch();
        };
        MoveToSystem moveTo = new MoveToSystem(
                spatial.transforms(),
                pathfinder,
                movement,
                moverConstraint);

        PathTransitionConstraint callerConstraint =
                new PathTransitionConstraint() {
                    @Override
                    public boolean allows(
                            int fromX,
                            int fromY,
                            int fromZ,
                            int toX,
                            int toY,
                            int toZ) {
                        return true;
                    }

                    @Override
                    public long revision() {
                        return 73L;
                    }
                };

        moveTo.start(objectId, 2, 0, 0, callerConstraint);

        assertFalse(allowedSeen[0]);
        assertEquals(73L, revisionSeen[0]);
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
