package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.control.movement.CancelMoveToCommand;
import io.github.evoforge.simulation.control.movement.CancelMoveToResult;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToResult;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;

final class MoveToCancellationIntegrationTest {

    @Test
    void cancellationLetsCurrentAtomicEdgeFinishButStartsNoFurtherStep() {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(-1, 5, -1, 1, -1, 1);
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:cancel_ground");
        ObjectDefinitionId moverDefinition = assembly.objectDefinition("test:cancel_mover");
        assembly.movementRate(moverDefinition, 100L);
        assembly.exclusiveOccupancy(moverDefinition);
        for (int x = -1; x <= 5; x++) {
            for (int y = -1; y <= 1; y++) {
                assembly.placeTerrain(x, y, -1, ground);
            }
        }
        ObjectId mover = assembly.createObject(moverDefinition);
        assembly.placeObject(mover, 0, 0, 0);

        SimulationRuntime runtime = assembly.start();
        MoveToResult start = runtime.submit(new MoveToCommand(mover, 4, 0, 0));
        assertTrue(start.accepted());
        assertTrue(runtime.view().moveTo().isActive(mover));

        CancelMoveToResult cancel = runtime.submit(new CancelMoveToCommand(mover));
        assertTrue(cancel.accepted());
        assertTrue(runtime.view().moveTo().isActive(mover),
                "current timed edge remains owned until its scheduled completion");

        for (int tick = 0; tick < 32 && runtime.view().moveTo().isActive(mover); tick++) {
            runtime.stepper().advance();
        }

        assertFalse(runtime.view().moveTo().isActive(mover));
        assertEquals(1, runtime.view().transforms().x(mover),
                "cancellation may finish the atomic edge but must not continue the route");
        assertEquals(0, runtime.view().transforms().y(mover));
        assertEquals(0, runtime.view().transforms().z(mover));
        assertEquals(
                "movement:move_to_cancelled",
                runtime.view().moveTo().lastCompletion(mover).code().value());
    }

    @Test
    void cancellingWithoutActiveOrderIsRejected() {
        SimulationAssembly assembly = SimulationAssembly.create();
        ObjectDefinitionId moverDefinition = assembly.objectDefinition("test:idle_cancel_mover");
        assembly.movementRate(moverDefinition, 100L);
        ObjectId mover = assembly.createObject(moverDefinition);

        SimulationRuntime runtime = assembly.start();
        CancelMoveToResult cancel = runtime.submit(new CancelMoveToCommand(mover));

        assertFalse(cancel.accepted());
        assertEquals("movement:no_active_move_to", cancel.code().value());
    }
}
