package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** First visual proof of generic autonomous opportunity selection. */
public final class CowForagingScenario implements VisualizerScenario {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");
    @Override public String id() { return "agent-cow-foraging"; }
    @Override public String title() { return "Cow Foraging"; }
    @Override public String description() {
        return "A hungry autonomous cow sees only through its directional visual sense, evaluates visible Grass and Hay, and chooses the more valuable source without a cow-specific behavior script.";
    }
    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:agent_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("scenario:cow");
        ObjectDefinitionId grass = assembly.objectDefinition("scenario:grass");
        ObjectDefinitionId hay = assembly.objectDefinition("scenario:hay");
        assembly.movementRate(cow, 500); assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE); assembly.vision(cow, 8, 120); assembly.need(cow, HUNGER, 100, 60);
        assembly.satisfiesNeed(grass, HUNGER, 15, GRAZE); assembly.satisfiesNeed(hay, HUNGER, 60, GRAZE);
        ScenarioTerrain.fill(assembly, ground, -1, 7, -2, 2, -1);
        ObjectId cowId = assembly.createObject(cow); ObjectId grassId = assembly.createObject(grass); ObjectId hayId = assembly.createObject(hay);
        assembly.placeObject(cowId, 0, 0, 0); assembly.placeObject(grassId, 2, 1, 0); assembly.placeObject(hayId, 6, -1, 0);
        assembly.initialFacing(cowId, 1, 0);
        SimulationRuntime runtime = assembly.start();
        CowForagingController controller = new CowForagingController(runtime, cowId, grassId, hayId, HUNGER);
        return new ScenarioSession(runtime, new ScenarioView(0, 3f, 0f, 0.85f), controller);
    }
}
