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

/** Visual proof that general knowledge can trigger information-seeking without concrete source knowledge. */
public final class CowVisualSearchScenario implements VisualizerScenario {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Override public String id() { return "agent-cow-visual-search"; }
    @Override public String title() { return "Cow Visual Search"; }
    @Override public String description() {
        return "A hungry cow starts facing away from food. It knows hunger has environmental solutions but knows no source location, so it scans with Vision until Grass actually enters perception.";
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
        assembly.vision(cow, 5, 100);
        assembly.need(cow, HUNGER, 100, 70);
        assembly.knowsNeedSolution(cow, HUNGER);
        assembly.satisfiesNeed(grass, HUNGER, 35, GRAZE);
        ScenarioTerrain.fill(assembly, ground, -2, 5, -4, 4, -1);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassId = assembly.createObject(grass);
        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassId, 3, 0, 0);
        assembly.initialFacing(cowId, -1, 0);

        SimulationRuntime runtime = assembly.start();
        CowVisualSearchController controller = new CowVisualSearchController(runtime, cowId, grassId, HUNGER);
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 1.5f, 0f, 0.82f),
                controller);
    }
}
