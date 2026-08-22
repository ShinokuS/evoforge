package io.github.evoforge.simulation.mechanics.movement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.DefinitionRegistry;
import io.github.evoforge.simulation.kernel.operation.OperationResults;
import io.github.evoforge.simulation.kernel.scheduling.BoundProcessScheduler;
import io.github.evoforge.simulation.kernel.scheduling.HandlerId;
import io.github.evoforge.simulation.kernel.scheduling.HandlerRegistry;
import io.github.evoforge.simulation.kernel.scheduling.Scheduler;
import io.github.evoforge.simulation.kernel.time.SimulationClock;
import io.github.evoforge.simulation.kernel.scheduling.SimulationStepper;
import io.github.evoforge.simulation.world.landscape.LandscapeSystem;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.terrain.TerrainSystem;
import io.github.evoforge.simulation.world.terrain.storage.SparseTerrainStorage;
import io.github.evoforge.simulation.world.geometry.GeometrySystem;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyDefinitions;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyState;
import io.github.evoforge.simulation.world.space.occupancy.OccupancySystem;
import io.github.evoforge.simulation.world.navigation.traversal.TransitionCost;
import io.github.evoforge.simulation.world.navigation.NavigationSystem;
import io.github.evoforge.simulation.world.object.ObjectFactory;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectRepository;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.spatial.SpatialSystem;
import io.github.evoforge.simulation.world.spatial.indexes.CellSpatialIndex;
import org.junit.jupiter.api.Test;

final class MovementRevalidationIntegrationTest {

    @Test
    void interruptedTransitionCleansStateBeforePublishingCompletion() {
        DefinitionRegistry<LandscapeDefinitionId> landscapeDefinitions =
                new DefinitionRegistry<>(LandscapeDefinitionId::of, LandscapeDefinitionId::asInt);
        LandscapeDefinitionId ground = landscapeDefinitions.register("test:ground");
        landscapeDefinitions.freeze();

        TerrainSystem terrain = new TerrainSystem(new SparseTerrainStorage(), landscapeDefinitions);
        GeometrySystem geometry = new GeometrySystem(terrain.lookup());
        LandscapeSystem landscape = new LandscapeSystem(terrain, geometry);
        NavigationSystem navigation = new NavigationSystem(geometry.lookup());
        OperationResults.requireAccepted(landscape.placeTerrain(0, 0, -1, ground));
        OperationResults.requireAccepted(landscape.placeTerrain(1, 0, -1, ground));

        DefinitionRegistry<ObjectDefinitionId> objectDefinitions =
                new DefinitionRegistry<>(ObjectDefinitionId::of, ObjectDefinitionId::asInt);
        ObjectDefinitionId walker = objectDefinitions.register("test:walker");
        objectDefinitions.freeze();

        ObjectRepository objects = new ObjectRepository();
        ObjectFactory objectFactory = new ObjectFactory(objects, objectDefinitions);
        WorldObject object = objectFactory.create(walker);
        ObjectId objectId = object.id();
        CellSpatialIndex cells = new CellSpatialIndex();
        SpatialSystem spatial = new SpatialSystem(cells);
        spatial.place(objectId, 0, 0, 0);

        OccupancyDefinitions occupancyDefinitions = new OccupancyDefinitions();
        occupancyDefinitions.put(walker, true);
        occupancyDefinitions.freeze();
        OccupancySystem occupancy = new OccupancySystem(
                objects, cells.lookup(), occupancyDefinitions);

        MovementDefinitions movementDefinitions = new MovementDefinitions();
        movementDefinitions.put(walker, MovementRate.of(100));
        movementDefinitions.freeze();
        MovementStateStore movementState = new MovementStateStore();

        HandlerRegistry handlers = new HandlerRegistry();
        Scheduler scheduler = new Scheduler(handlers);
        SimulationClock clock = new SimulationClock();
        SimulationStepper stepper = new SimulationStepper(clock, scheduler);
        MovementStepCompletion[] completion = new MovementStepCompletion[1];
        boolean[] cleanupVisible = new boolean[1];

        MovementActionProcessor actions = new MovementActionProcessor(
                movementState,
                objects,
                spatial.transforms(),
                navigation.lookup(),
                occupancy,
                spatial,
                value -> {
                    cleanupVisible[0] =
                            !movementState.isMoving(objectId)
                                    && movementState.activeActionCount() == 0
                                    && occupancy.reservationCount() == 0;
                    completion[0] = value;
                });
        HandlerId movementHandler = handlers.register(actions::complete);
        MovementSystem movement = new MovementSystem(
                objects,
                spatial.transforms(),
                navigation.lookup(),
                movementDefinitions,
                (fromX, fromY, fromZ, toX, toY, toZ) -> TransitionCost.of(1000),
                occupancy,
                movementState,
                new BoundProcessScheduler(clock, scheduler, movementHandler));

        assertTrue(movement.startStep(objectId, 1, 0, 0).accepted());
        assertEquals(OccupancyState.RESERVED, occupancy.state(1, 0, 0));
        OperationResults.requireAccepted(landscape.removeTerrain(1, 0, -1));

        for (int tick = 0; tick < 10; tick++) stepper.advance();

        assertEquals(0, spatial.transforms().x(objectId));
        assertFalse(movementState.isMoving(objectId));
        assertEquals(0, movementState.activeActionCount());
        assertEquals(OccupancyState.FREE, occupancy.state(1, 0, 0));
        assertEquals(0, occupancy.reservationCount());
        assertNotNull(completion[0]);
        assertTrue(cleanupVisible[0]);
        assertFalse(completion[0].committed());
        assertEquals("movement:transition_unavailable", completion[0].code().value());
    }

    @Test
    void staleClaimCannotReleaseNewMovementOwner() {
        MovementStateStore state = new MovementStateStore();
        ObjectId objectId = ObjectId.of(7, 0);

        MovementClaimId first = state.tryAcquireClaim(objectId);
        assertNotNull(first);
        assertTrue(state.releaseClaim(first, objectId));

        MovementClaimId second = state.tryAcquireClaim(objectId);
        assertNotNull(second);
        assertFalse(state.releaseClaim(first, objectId));
        assertTrue(state.ownsClaim(second, objectId));
        assertEquals(1, state.activeClaimCount());
        assertTrue(state.releaseClaim(second, objectId));
        assertEquals(0, state.activeClaimCount());
    }
}
