package io.github.evoforge.simulation.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.space.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCost;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import org.junit.jupiter.api.Test;

final class MovementTimingTest {

    @Test
    void movementSchedulesAtLeastOneTickWhenRateExceedsTransitionCost() {
        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        ObjectDefinitionId walker = objectDefinitions.register("test:walker");
        objectDefinitions.freeze();

        ObjectRepository objects = new ObjectRepository();
        ObjectId objectId = new ObjectFactory(objects, objectDefinitions).create(walker).id();
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
        movementDefinitions.put(walker, MovementRate.of(10_000));
        movementDefinitions.freeze();

        long[] scheduledDelay = {-1};
        MovementSystem movement = new MovementSystem(
                objects,
                spatial.transforms(),
                (x, y, z) -> TransitionMask.of(1, 0, 0),
                movementDefinitions,
                (fromX, fromY, fromZ, toX, toY, toZ) -> TransitionCost.of(1),
                occupancy,
                new MovementStateStore(),
                (delayTicks, processId) -> scheduledDelay[0] = delayTicks);

        assertTrue(movement.startStep(objectId, 1, 0, 0).accepted());
        assertEquals(1, scheduledDelay[0]);
    }
}
