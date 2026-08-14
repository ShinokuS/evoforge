package io.github.evoforge.simulation.world.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.result.OperationResults;
import io.github.evoforge.simulation.time.BoundProcessScheduler;
import io.github.evoforge.simulation.time.HandlerId;
import io.github.evoforge.simulation.time.HandlerRegistry;
import io.github.evoforge.simulation.time.Scheduler;
import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.landscape.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.mechanics.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.mechanics.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.mechanics.traversal.MoverTraversalConstraint;
import io.github.evoforge.simulation.world.mechanics.traversal.TransitionCost;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;

final class MoverTraversalRevalidationTest {

    @Test
    void restrictedDestinationRejectsMovementBeforeScheduling() {
        Fixture fixture = new Fixture(false);

        MovementStartAttempt attempt =
                fixture.movement.startStep(fixture.objectId, 1, 0, 0);

        assertFalse(attempt.accepted());
        assertEquals("movement:traversal_restricted", attempt.code().value());
        assertFalse(fixture.state.isMoving(fixture.objectId));
        assertEquals(0, fixture.state.activeActionCount());
    }

    @Test
    void destinationBecomingRestrictedCancelsCommit() {
        Fixture fixture = new Fixture(true);
        MovementStartAttempt attempt =
                fixture.movement.startStep(fixture.objectId, 1, 0, 0);
        assertTrue(attempt.accepted());

        fixture.allowed[0] = false;
        for (int tick = 0; tick < 10; tick++) {
            fixture.stepper.advance();
        }

        assertEquals(0, fixture.spatial.transforms().x(fixture.objectId));
        assertFalse(fixture.state.isMoving(fixture.objectId));
        assertNotNull(fixture.completion[0]);
        assertFalse(fixture.completion[0].committed());
        assertEquals(
                "movement:traversal_restricted",
                fixture.completion[0].code().value());
    }

    private static final class Fixture {
        private final boolean[] allowed = new boolean[1];
        private final MovementStepCompletion[] completion =
                new MovementStepCompletion[1];
        private final ObjectRepository objects = new ObjectRepository();
        private final CellSpatialIndex cells = new CellSpatialIndex();
        private final SpatialSystem spatial = new SpatialSystem(cells);
        private final MovementStateStore state = new MovementStateStore();
        private final ObjectId objectId;
        private final SimulationStepper stepper;
        private final MovementSystem movement;

        private Fixture(boolean initiallyAllowed) {
            allowed[0] = initiallyAllowed;

            DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions =
                    new DefinitionRegistry<>(
                            LandscapeDefinitionId::of,
                            LandscapeDefinitionId::asInt);
            LandscapeDefinitionId ground =
                    landscapeDefinitions.register("test:ground");
            landscapeDefinitions.freeze();
            TerrainSystem terrain = new TerrainSystem(
                    new SparseTerrainStorage(),
                    landscapeDefinitions);
            GeometrySystem geometry = new GeometrySystem(terrain.lookup());
            LandscapeSystem landscape = new LandscapeSystem(terrain, geometry);
            NavigationSystem navigation = new NavigationSystem(geometry.lookup());
            OperationResults.requireAccepted(
                    landscape.placeTerrain(0, 0, -1, ground));
            OperationResults.requireAccepted(
                    landscape.placeTerrain(1, 0, -1, ground));

            DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                    new DefinitionRegistry<>(
                            ObjectDefinitionId::of,
                            ObjectDefinitionId::asInt);
            ObjectDefinitionId walker =
                    objectDefinitions.register("test:walker");
            objectDefinitions.freeze();
            objectId = new ObjectFactory(objects, objectDefinitions)
                    .create(walker)
                    .id();
            spatial.place(objectId, 0, 0, 0);

            OccupancyDefinitions occupancyDefinitions =
                    new OccupancyDefinitions();
            occupancyDefinitions.freeze();
            OccupancySystem occupancy = new OccupancySystem(
                    objects,
                    cells.lookup(),
                    occupancyDefinitions);

            MovementDefinitions movementDefinitions =
                    new MovementDefinitions();
            movementDefinitions.put(walker, MovementRate.of(100));
            movementDefinitions.freeze();

            MoverTraversalConstraint traversal =
                    (moverId, fromX, fromY, fromZ, toX, toY, toZ) -> allowed[0];

            HandlerRegistry handlers = new HandlerRegistry();
            Scheduler scheduler = new Scheduler(handlers);
            SimulationClock clock = new SimulationClock();
            stepper = new SimulationStepper(clock, scheduler);
            MovementActionProcessor actions = new MovementActionProcessor(
                    state,
                    objects,
                    spatial.transforms(),
                    navigation.lookup(),
                    traversal,
                    occupancy,
                    spatial,
                    io.github.evoforge.simulation.world.spatial.orientation.OrientationMutations.none(),
                    value -> completion[0] = value);
            HandlerId movementHandler = handlers.register(actions::complete);

            movement = new MovementSystem(
                    objects,
                    spatial.transforms(),
                    navigation.lookup(),
                    movementDefinitions,
                    (fromX, fromY, fromZ, toX, toY, toZ) -> TransitionCost.of(1000),
                    traversal,
                    occupancy,
                    state,
                    new BoundProcessScheduler(clock, scheduler, movementHandler));
        }
    }
}
