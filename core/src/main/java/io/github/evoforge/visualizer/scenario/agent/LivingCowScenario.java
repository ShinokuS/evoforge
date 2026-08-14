package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioTerrain;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.LinkedHashMap;
import java.util.Map;

/** Full living-world acceptance slice: physiology, search, movement, timed use, depletion and regrowth. */
public final class LivingCowScenario implements VisualizerScenario {
    private static final NeedId HUNGER = NeedId.of("core:hunger");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    @Override public String id() { return "agent-living-cow"; }
    @Override public String title() { return "Living Cow Cycle"; }
    @Override public String description() {
        return "A satisfied Cow becomes meaningfully hungry, searches a sparse meadow, discovers finite plant patches, grazes over real simulation time, depletes biomass and must move again while plants regrow.";
    }

    @Override
    public ScenarioSession create() {
        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("scenario:living_meadow");
        ObjectDefinitionId cow = assembly.objectDefinition("scenario:living_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("scenario:living_grass");
        ObjectDefinitionId clover = assembly.objectDefinition("scenario:living_clover");
        ObjectDefinitionId dandelion = assembly.objectDefinition("scenario:living_dandelion");

        assembly.movementRate(cow, 650);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 5, 120);
        assembly.need(cow, HUNGER, 100, 0);
        assembly.needMotivation(cow, HUNGER, 36);
        assembly.needProgression(cow, HUNGER, 6, 4);
        assembly.knowsNeedSolution(cow, HUNGER);

        assembly.consumableStock(grass, 4, 2);
        assembly.growth(grass, 1, 28);
        assembly.satisfiesNeed(grass, HUNGER, 28, 2, 7, GRAZE);

        assembly.consumableStock(clover, 4, 2);
        assembly.growth(clover, 1, 34);
        assembly.satisfiesNeed(clover, HUNGER, 40, 2, 9, GRAZE);

        assembly.consumableStock(dandelion, 3, 1);
        assembly.growth(dandelion, 1, 40);
        assembly.satisfiesNeed(dandelion, HUNGER, 32, 1, 8, GRAZE);

        ScenarioTerrain.fill(assembly, ground, -18, 18, -14, 14, -1);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassEast = assembly.createObject(grass);
        ObjectId grassSouthEast = assembly.createObject(grass);
        ObjectId cloverNorth = assembly.createObject(clover);
        ObjectId cloverWest = assembly.createObject(clover);
        ObjectId dandelionSouth = assembly.createObject(dandelion);
        ObjectId dandelionNorthEast = assembly.createObject(dandelion);

        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassEast, 11, 1, 0);
        assembly.placeObject(grassSouthEast, 15, -10, 0);
        assembly.placeObject(cloverNorth, -3, 12, 0);
        assembly.placeObject(cloverWest, -14, 6, 0);
        assembly.placeObject(dandelionSouth, -9, -11, 0);
        assembly.placeObject(dandelionNorthEast, 14, 10, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        Map<ObjectId, String> names = new LinkedHashMap<>();
        names.put(cowId, "Cow");
        names.put(grassEast, "Grass");
        names.put(grassSouthEast, "Grass");
        names.put(cloverNorth, "Clover");
        names.put(cloverWest, "Clover");
        names.put(dandelionSouth, "Dandelion");
        names.put(dandelionNorthEast, "Dandelion");

        ObjectPresentationBindings presentations = new ObjectPresentationBindings(Map.of(
                cow, new ObjectPresentation(
                        "Cow",
                        "Autonomous herbivore. The drawing reflects orientation and the authoritative continuing-use phase.",
                        ObjectVisualFamily.CREATURE,
                        0),
                grass, new ObjectPresentation(
                        "Grass",
                        "Finite plant biomass using the shared stock/growth mechanics.",
                        ObjectVisualFamily.VEGETATION,
                        0),
                clover, new ObjectPresentation(
                        "Clover",
                        "A richer food definition on exactly the same generic plant mechanics.",
                        ObjectVisualFamily.VEGETATION,
                        1),
                dandelion, new ObjectPresentation(
                        "Dandelion",
                        "A flowering food definition with its own quantity, growth and interaction timing.",
                        ObjectVisualFamily.VEGETATION,
                        2)));

        LivingCowController controller = new LivingCowController(runtime, cowId, HUNGER, names);
        return new ScenarioSession(
                runtime,
                new ScenarioView(0, 0f, 0f, 0.58f),
                controller,
                presentations);
    }
}
