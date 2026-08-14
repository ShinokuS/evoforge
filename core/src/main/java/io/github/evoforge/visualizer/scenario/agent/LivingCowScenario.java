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
        return "A satisfied Cow becomes hungry, perceives/searches, chooses finite plants, grazes over real simulation time, depletes biomass and revisits regrown food.";
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
        assembly.vision(cow, 6, 120);
        assembly.need(cow, HUNGER, 100, 0);
        assembly.needProgression(cow, HUNGER, 8, 4);
        assembly.knowsNeedSolution(cow, HUNGER);

        assembly.consumableStock(grass, 10, 7);
        assembly.growth(grass, 1, 10);
        assembly.satisfiesNeed(grass, HUNGER, 20, 2, 5, GRAZE);

        assembly.consumableStock(clover, 8, 5);
        assembly.growth(clover, 1, 14);
        assembly.satisfiesNeed(clover, HUNGER, 34, 2, 7, GRAZE);

        assembly.consumableStock(dandelion, 6, 3);
        assembly.growth(dandelion, 1, 18);
        assembly.satisfiesNeed(dandelion, HUNGER, 27, 1, 6, GRAZE);

        ScenarioTerrain.fill(assembly, ground, -7, 11, -6, 6, -1);

        ObjectId cowId = assembly.createObject(cow);
        ObjectId grassNear = assembly.createObject(grass);
        ObjectId grassFar = assembly.createObject(grass);
        ObjectId cloverNorth = assembly.createObject(clover);
        ObjectId cloverWest = assembly.createObject(clover);
        ObjectId dandelionSouth = assembly.createObject(dandelion);
        ObjectId dandelionEast = assembly.createObject(dandelion);

        assembly.placeObject(cowId, 0, 0, 0);
        assembly.placeObject(grassNear, 3, 1, 0);
        assembly.placeObject(grassFar, 8, -2, 0);
        assembly.placeObject(cloverNorth, 1, 5, 0);
        assembly.placeObject(cloverWest, -5, 2, 0);
        assembly.placeObject(dandelionSouth, -2, -4, 0);
        assembly.placeObject(dandelionEast, 6, 4, 0);
        assembly.initialFacing(cowId, 1, 0);

        SimulationRuntime runtime = assembly.start();
        Map<ObjectId, String> names = new LinkedHashMap<>();
        names.put(cowId, "Cow");
        names.put(grassNear, "Grass");
        names.put(grassFar, "Grass");
        names.put(cloverNorth, "Clover");
        names.put(cloverWest, "Clover");
        names.put(dandelionSouth, "Dandelion");
        names.put(dandelionEast, "Dandelion");

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
                new ScenarioView(0, 2f, 0f, 0.68f),
                controller,
                presentations);
    }
}
