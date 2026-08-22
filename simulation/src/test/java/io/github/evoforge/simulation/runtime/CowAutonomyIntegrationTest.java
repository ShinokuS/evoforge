package io.github.evoforge.simulation.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.search.AgentSearchStatus;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import org.junit.jupiter.api.Test;

final class CowAutonomyIntegrationTest {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Test
    void hungryCowPerceivesFoodMovesAndSatisfiesNeed() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:cow_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:grass");
        configureCow(assembly, cow, 8, 80);
        assembly.satisfiesNeed(grass, HUNGER, 30, GRAZE);
        line(assembly, ground, 0, 2);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 2, 0, 0);
        SimulationRuntime runtime = assembly.start();
        advance(runtime, 3);
        assertEquals(2, runtime.view().positions().x(cowId));
        assertEquals(50, runtime.view().needs().level(cowId, HUNGER));
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cowId);
        assertNotNull(trace);
        assertEquals(1, trace.tick());
        assertEquals(objectTargetKey(grassId), trace.selected().targetKey());
        assertEquals("needs:satisfaction", trace.selected().providerId());
        assertEquals("core:hunger", trace.selected().motivation());
    }

    @Test
    void newHayDefinitionIsUsedWithoutDecisionCodeOrCowBehaviorChanges() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:hay_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId hay = assembly.objectDefinition("test:hay");
        configureCow(assembly, cow, 6, 70);
        assembly.satisfiesNeed(hay, HUNGER, 40, GRAZE);
        line(assembly, ground, 0, 2);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId hayId = assembly.createObject(hay);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(hayId, 2, 0, 0);
        SimulationRuntime runtime = assembly.start();
        advance(runtime, 3);
        assertEquals(
                objectTargetKey(hayId),
                runtime.view().agents().lastDecision(cowId).selected().targetKey());
        assertEquals(30, runtime.view().needs().level(cowId, HUNGER));
    }

    @Test
    void cowCanPreferFartherHigherBenefitSourceOverNearbyWeakSource() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:choice_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:weak_grass");
        ObjectDefinitionId hay = assembly.objectDefinition("test:strong_hay");
        configureCow(assembly, cow, 8, 80);
        assembly.satisfiesNeed(grass, HUNGER, 10, GRAZE);
        assembly.satisfiesNeed(hay, HUNGER, 60, GRAZE);
        line(assembly, ground, 0, 4);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        ObjectId hayId = assembly.createObject(hay);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 1, 0, 0);
        assembly.placeObject(hayId, 4, 0, 0);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cowId);
        assertNotNull(trace);
        assertEquals(2, trace.candidates().size());
        assertEquals(objectTargetKey(hayId), trace.selected().targetKey());
        assertEquals(objectTargetKey(hayId), runtime.view().agents().currentTargetKey(cowId));
        assertEquals(60, trace.selected().expectedBenefit());
    }

    @Test
    void cowDoesNotKnowAboutFoodOutsideCurrentVisionRange() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:perception_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:distant_grass");
        configureCow(assembly, cow, 2, 80);
        assembly.satisfiesNeed(grass, HUNGER, 50, GRAZE);
        line(assembly, ground, 0, 4);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 4, 0, 0);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cowId);
        assertNotNull(trace);
        assertEquals(0, trace.candidates().size());
        assertNull(trace.selected());
        assertNull(runtime.view().agents().currentTargetKey(cowId));
        assertEquals(0, runtime.view().positions().x(cowId));
        assertEquals(80, runtime.view().needs().level(cowId, HUNGER));
    }

    @Test
    void foodBehindCowIsNotAVisualCandidate() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:fov_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:grass");
        configureCow(assembly, cow, 4, 80);
        assembly.satisfiesNeed(grass, HUNGER, 20, GRAZE);
        line(assembly, ground, -2, 2);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId front = assembly.createObject(grass);
        ObjectId behind = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(front, 2, 0, 0);
        assembly.placeObject(behind, -2, 0, 0);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cowId);
        assertEquals(1, trace.candidates().size());
        assertEquals(objectTargetKey(front), trace.selected().targetKey());
        assertFalse(runtime.view().vision().snapshot(cowId).isObjectVisible(behind));
    }

    @Test
    void opaqueTerrainBlocksFoodFromVision() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:occlusion_ground");
        MaterialDefinitionId wall = assembly.landscapeDefinition("test:wall");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:grass");
        configureCow(assembly, cow, 5, 80);
        assembly.satisfiesNeed(grass, HUNGER, 20, GRAZE);
        line(assembly, ground, 0, 3);
        assembly.placeTerrain(1, 0, 0, wall);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 3, 0, 0);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        assertEquals(0, runtime.view().agents().lastDecision(cowId).candidates().size());
        assertFalse(runtime.view().vision().snapshot(cowId).isObjectVisible(grassId));
    }

    @Test
    void knownNeedSolutionStartsSearchAndFindsFoodOnlyAfterTurningTowardIt() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:search_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:search_grass");
        configureCow(assembly, cow, 4, 80);
        assembly.knowsNeedSolution(cow, HUNGER);
        assembly.satisfiesNeed(grass, HUNGER, 30, GRAZE);
        line(assembly, ground, -1, 3);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 3, 0, 0);
        assembly.initialFacing(cowId, -1, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        AgentDecisionTrace first = runtime.view().agents().lastDecision(cowId);
        assertNotNull(first);
        assertNull(first.selected());
        assertFalse(runtime.view().vision().snapshot(cowId).isObjectVisible(grassId));
        var search = runtime.view().searches().currentSearch(cowId);
        assertNotNull(search);
        assertEquals(AgentSearchStatus.SWEEPING, search.status());
        assertEquals("core:hunger", search.motivation());
        assertEquals(FacingDirection.of(0, 1), search.facing());

        runtime.stepper().advance();
        assertEquals(FacingDirection.EAST, runtime.view().orientations().facing(cowId));
        assertNull(runtime.view().agents().lastDecision(cowId).selected());

        runtime.stepper().advance();
        AgentDecisionTrace found = runtime.view().agents().lastDecision(cowId);
        assertNotNull(found.selected());
        assertEquals(objectTargetKey(grassId), found.selected().targetKey());
        assertEquals(objectTargetKey(grassId), runtime.view().agents().currentTargetKey(cowId));
        assertNull(runtime.view().searches().currentSearch(cowId));
    }

    @Test
    void unseenFoodDoesNotTriggerSearchWithoutGeneralSolutionKnowledge() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:no_knowledge_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:unknown_grass");
        configureCow(assembly, cow, 4, 80);
        assembly.satisfiesNeed(grass, HUNGER, 30, GRAZE);
        line(assembly, ground, -1, 3);
        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 3, 0, 0);
        assembly.initialFacing(cowId, -1, 0);

        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        assertNull(runtime.view().agents().lastDecision(cowId).selected());
        assertNull(runtime.view().searches().currentSearch(cowId));
        assertEquals(FacingDirection.of(-1, 0), runtime.view().orientations().facing(cowId));
    }

    @Test
    void equalCandidatesUseStableObjectIdTieBreak() {
        SimulationAssembly assembly = SimulationAssembly.create();
        MaterialDefinitionId ground = assembly.landscapeDefinition("test:tie_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("test:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("test:tie_grass");
        configureCow(assembly, cow, 3, 80);
        assembly.satisfiesNeed(grass, HUNGER, 20, GRAZE);
        for (int x = 0; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) assembly.placeTerrain(x, y, -1, ground);
        }
        ObjectId cowId = assembly.createObject(cow);
        ObjectId first = assembly.createObject(grass);
        ObjectId second = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(first, 1, 1, 0);
        assembly.placeObject(second, 1, -1, 0);
        SimulationRuntime runtime = assembly.start();
        runtime.stepper().advance();
        AgentDecisionTrace trace = runtime.view().agents().lastDecision(cowId);
        assertEquals(objectTargetKey(first), trace.selected().targetKey());
        assertEquals(2, trace.candidates().size());
    }

    private static void configureCow(
            SimulationAssembly assembly,
            ObjectDefinitionId cow,
            int visionRange,
            long hunger) {
        assembly.movementRate(cow, 10_000);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, visionRange, 120);
        assembly.need(cow, HUNGER, 100, hunger);
    }

    private static void line(
            SimulationAssembly assembly,
            MaterialDefinitionId ground,
            int minX,
            int maxX) {
        for (int x = minX; x <= maxX; x++) assembly.placeTerrain(x, 0, -1, ground);
    }

    private static void advance(SimulationRuntime runtime, int ticks) {
        for (int tick = 0; tick < ticks; tick++) runtime.stepper().advance();
    }

    private static String objectTargetKey(ObjectId objectId) {
        return "object:" + objectId.asLong();
    }
}
