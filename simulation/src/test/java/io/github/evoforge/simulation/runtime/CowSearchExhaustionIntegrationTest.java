package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.search.AgentSearchStatus;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class CowSearchExhaustionIntegrationTest {

    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void fullLocalSweepExhaustsWithoutInventingAConcreteSource() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:search_exhaustion_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:search_exhaustion_cow");

        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 4, 100);
        assembly.need(cow, HUNGER, 100, 80);
        assembly.knowsNeedSolution(cow, HUNGER);
        assembly.placeTerrain(0, 0, -1, ground);

        ObjectId cowId = assembly.createObject(cow);
        assembly.placeObject(cowId, 0, 0, 0);

        SimulationRuntime runtime = assembly.start();
        for (int tick = 0; tick < 4; tick++) {
            runtime.stepper().advance();
        }

        assertNull(runtime.view().agents().currentTarget(cowId));
        assertNull(runtime.view().searches().currentSearch(cowId));
        var exhausted = runtime.view().searches().lastSearch(cowId);
        assertEquals(AgentSearchStatus.LOCAL_SWEEP_EXHAUSTED, exhausted.status());
        assertEquals(4, exhausted.headingsObserved());
        assertEquals("core:hunger", exhausted.motivation());
        assertEquals(0, runtime.view().transforms().x(cowId));
        assertEquals(0, runtime.view().transforms().y(cowId));
        assertEquals(80, runtime.view().needs().level(cowId, HUNGER));
    }
}
