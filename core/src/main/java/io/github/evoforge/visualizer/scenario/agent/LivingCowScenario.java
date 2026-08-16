package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.environment.precipitation.PrecipitationSchedule;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfiles;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Integrated living-world acceptance scene for needs, plants, Water and a changing climate. */
public final class LivingCowScenario implements VisualizerScenario {
    static final int MIN_X = -18;
    static final int MAX_X = 18;
    static final int MIN_Y = -14;
    static final int MAX_Y = 14;
    static final int STANDING_Z = 1;
    static final int LAKE_WATER_Z = 0;
    static final int LAKE_MIN_X = 15;
    static final int LAKE_MAX_X = 18;
    static final int LAKE_MIN_Y = -3;
    static final int LAKE_MAX_Y = 3;

    static final long CLIMATE_CYCLE_TICKS = 360L;
    static final long RAIN_ACTIVE_TICKS = 120L;
    private static final int RAIN_PULSE_VOLUME = 20;
    private static final int EVAPORATION_PER_EVENT = 60;
    private static final int INITIAL_LAKE_DEPTH = 80_000;

    static final NeedId HUNGER = NeedId.of("core:hunger");
    static final NeedId THIRST = NeedId.of("core:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    // A handful of low-infiltration micro-basins are embedded in an otherwise absorbent meadow.
    // They are scenario terrain, not pre-seeded Water: puddles appear only after enough rain falls.
    private static final int[][] PUDDLE_BASIN_CENTERS = {
            {2, 1}, {-6, 5}, {7, 6}, {-9, -6}, {7, -7}
    };

    @Override public String id() { return "agent-living-cow"; }
    @Override public String title() { return "Living Cow Meadow"; }
    @Override public String description() {
        return "Two autonomous Cows balance Hunger and Thirst in a changing meadow: plants regrow, a permanent lake sits at the map edge, and light cyclic rain creates only a few temporary puddles in shallow micro-basins.";
    }

    @Override
    public ScenarioSession create() {
        PrecipitationSchedule rainSchedule = PrecipitationSchedule.cyclic(
                RAIN_PULSE_VOLUME,
                1L,
                RAIN_ACTIVE_TICKS,
                CLIMATE_CYCLE_TICKS);

        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, -2, 3);
        LandscapeDefinitionId meadow = assembly.landscapeDefinition("scenario:living_meadow");
        LandscapeDefinitionId basin = assembly.landscapeDefinition("scenario:living_puddle_basin");
        LandscapeDefinitionId lakeFloor = assembly.landscapeDefinition("scenario:living_lake_floor");
        ObjectDefinitionId cow = assembly.objectDefinition("scenario:living_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("scenario:living_grass");
        ObjectDefinitionId clover = assembly.objectDefinition("scenario:living_clover");
        ObjectDefinitionId dandelion = assembly.objectDefinition("scenario:living_dandelion");

        // Most meadow soil absorbs this light shower completely. Only deterministic shallow
        // micro-basins saturate early enough to retain visible free Water at standing level.
        assembly.soilProperties(meadow, 6_000, 6_000);
        assembly.soilPropertiesVariation(meadow, 0x51A7E11DL, 1_000);
        assembly.surfaceRetention(meadow, 500);
        assembly.soilProperties(basin, 600, 1_000);
        assembly.soilPropertiesVariation(basin, 0xB451A11L, 200);
        assembly.surfaceRetention(basin, 2_000);
        assembly.surfaceRetention(lakeFloor, 500);
        assembly.precipitation(rainSchedule);
        assembly.periodicEvaporation(EVAPORATION_PER_EVENT, 4L);

        assembly.movementRate(cow, 650);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, 8, 140);
        assembly.need(cow, HUNGER, 100, 0);
        assembly.needMotivation(cow, HUNGER, 36);
        assembly.needProgression(cow, HUNGER, 4, 8);
        assembly.knowsNeedSolution(cow, HUNGER);
        assembly.need(cow, THIRST, 100, 0);
        assembly.needMotivation(cow, THIRST, 45);
        assembly.needProgression(cow, THIRST, 5, 6);
        assembly.knowsNeedSolution(cow, THIRST);
        assembly.physicalCellVolumeMilliliters(1_000_000L);
        assembly.drinksLiquid(
                cow,
                THIRST,
                WaterSystem.TYPE,
                2_000L,
                25L,
                3L,
                InteractionReachProfiles.cardinalSameOrOneBelow());

        assembly.consumableStock(grass, 4, 2);
        assembly.growth(grass, 1, 28);
        assembly.satisfiesNeed(grass, HUNGER, 28, 2, 7, GRAZE);
        assembly.consumableStock(clover, 4, 2);
        assembly.growth(clover, 1, 34);
        assembly.satisfiesNeed(clover, HUNGER, 40, 2, 9, GRAZE);
        assembly.consumableStock(dandelion, 3, 1);
        assembly.growth(dandelion, 1, 40);
        assembly.satisfiesNeed(dandelion, HUNGER, 32, 1, 8, GRAZE);

        for (int x = MIN_X; x <= MAX_X; x++) {
            for (int y = MIN_Y; y <= MAX_Y; y++) {
                if (insideLake(x, y)) {
                    assembly.placeTerrain(x, y, -1, lakeFloor);
                    assembly.initialWater(x, y, LAKE_WATER_Z, INITIAL_LAKE_DEPTH);
                } else {
                    assembly.placeTerrain(x, y, 0, isPuddleBasin(x, y) ? basin : meadow);
                }
            }
        }

        ObjectId meadowCow = assembly.createObject(cow);
        ObjectId lakeCow = assembly.createObject(cow);
        ObjectId grassEast = assembly.createObject(grass);
        ObjectId grassSouthEast = assembly.createObject(grass);
        ObjectId cloverNorth = assembly.createObject(clover);
        ObjectId cloverWest = assembly.createObject(clover);
        ObjectId dandelionSouth = assembly.createObject(dandelion);
        ObjectId dandelionNorthEast = assembly.createObject(dandelion);

        assembly.placeObject(meadowCow, 0, 0, STANDING_Z);
        assembly.placeObject(lakeCow, 12, 0, STANDING_Z);
        assembly.placeObject(grassEast, 5, 1, STANDING_Z);
        assembly.placeObject(grassSouthEast, 10, -9, STANDING_Z);
        assembly.placeObject(cloverNorth, -3, 11, STANDING_Z);
        assembly.placeObject(cloverWest, -13, 5, STANDING_Z);
        assembly.placeObject(dandelionSouth, -8, -10, STANDING_Z);
        assembly.placeObject(dandelionNorthEast, 10, 9, STANDING_Z);
        assembly.initialFacing(meadowCow, 1, 0);
        assembly.initialFacing(lakeCow, 1, 0);

        SimulationRuntime runtime = assembly.start();
        Map<ObjectId, String> names = new LinkedHashMap<>();
        names.put(meadowCow, "Meadow Cow");
        names.put(lakeCow, "Lake Cow");
        names.put(grassEast, "Grass");
        names.put(grassSouthEast, "Grass");
        names.put(cloverNorth, "Clover");
        names.put(cloverWest, "Clover");
        names.put(dandelionSouth, "Dandelion");
        names.put(dandelionNorthEast, "Dandelion");

        ObjectPresentationBindings presentations = new ObjectPresentationBindings(Map.of(
                cow, new ObjectPresentation(
                        "Cow",
                        "Autonomous herbivore balancing Hunger and Thirst through one Utility decision layer.",
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

        LivingCowController controller = new LivingCowController(
                runtime,
                List.of(meadowCow, lakeCow),
                HUNGER,
                THIRST,
                names);
        WeatherPresentationLookup weather = () -> rainSchedule.activeAt(runtime.time().tick())
                ? WeatherPresentation.rain(0.55f)
                : WeatherPresentation.CLEAR;
        return new ScenarioSession(
                runtime,
                new ScenarioView(STANDING_Z, 0f, 0f, 0.54f),
                controller,
                presentations,
                weather);
    }

    static boolean insideLake(int x, int y) {
        return x >= LAKE_MIN_X && x <= LAKE_MAX_X && y >= LAKE_MIN_Y && y <= LAKE_MAX_Y;
    }

    static boolean isPuddleBasin(int x, int y) {
        for (int[] center : PUDDLE_BASIN_CENTERS) {
            int dx = Math.abs(x - center[0]);
            int dy = Math.abs(y - center[1]);
            if (dx + dy <= 1) return true;
        }
        return false;
    }
}
