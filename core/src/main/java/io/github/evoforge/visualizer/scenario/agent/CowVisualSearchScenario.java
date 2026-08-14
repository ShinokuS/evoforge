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

/** Visual proof of coordinate-free unknown-source search expanding beyond the initial field of view. */
public final class CowVisualSearchScenario implements VisualizerScenario {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Override public String id() { return "agent-cow-visual-search"; }
    @Override public String title() { return "Cow Visual Search"; }
    @Override public String description() {
        return "Grass starts well outside the cow's Vision. The hungry cow explores by relative visible legs, discovers it through Vision, then consumes one unit from its finite stock.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:search_ground");
        ObjectDefinitionId cow = assembly.objectDefinition("scenario:search_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("scenario:search_grass");
        assembly.movementRate(cow, 500);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 6, 110);
        assembly.need(cow, HUNGER, 100, 70);
        assembly.knowsNeedSolution(cow, HUNGER);
        assembly.consumableStock(grass, 6, 6);
        assembly.satisfiesNeed(grass, HUNGER, 35, 1, GRAZE);
        ScenarioTerrain.fill(assembly, ground, -6, 20, -8, 8, -1);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 14, 0, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        CowVisualSearchController controller = new CowVisualSearchController(runtime, cowId, grassId, HUNGER);
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 7f, 0f, 0.64f),
                controller);
    }
}
