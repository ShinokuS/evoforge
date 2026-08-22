package io.github.evoforge.visualizer.scenario.agent;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.agents.CapabilityId;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.mechanics.hydrology.PrecipitationSchedule;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import io.github.evoforge.simulation.world.interaction.InteractionReachProfiles;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Larger multi-agent living-world scene for observing contention and emergent routing. */
public final class LivingCowHerdScenario implements VisualizerScenario {
    static final int MIN_X = -24;
    static final int MAX_X = 24;
    static final int MIN_Y = -17;
    static final int MAX_Y = 17;
    static final int STANDING_Z = 1;
    static final int LAKE_WATER_Z = 0;
    static final int LAKE_CENTER_X = 10;
    static final int LAKE_CENTER_Y = 0;
    static final int LAKE_RADIUS_X = 7;
    static final int LAKE_RADIUS_Y = 6;

    static final long CLIMATE_CYCLE_TICKS = 360L;
    static final long RAIN_ACTIVE_TICKS = 120L;
    private static final int RAIN_PULSE_VOLUME = 20;
    private static final int EVAPORATION_PER_EVENT = 60;
    private static final int INITIAL_LAKE_DEPTH = 80_000;
    private static final int COW_MAX_WADING_DEPTH = 10_000;
    private static final int COW_VISION_RANGE = 10;
    private static final int COW_HORIZONTAL_FOV_DEGREES = 330;

    static final NeedId HUNGER = NeedId.of("core:hunger");
    static final NeedId THIRST = NeedId.of("core:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("core:graze");

    static final int[][] COW_STARTS = {
            {-18, -9}, {-14, 10}, {-5, -8}, {-2, 9}, {10, 8}, {20, -2}
    };
    private static final int[][] COW_FACING = {
            {1, 0}, {1, 0}, {0, 1}, {1, 0}, {-1, 0}, {-1, 0}
    };
    static final int[][] PLANT_SITES = {
            {-21, -13, 0}, {-20, -5, 1}, {-20, 5, 2}, {-18, 13, 0},
            {-15, -12, 1}, {-14, -3, 0}, {-14, 6, 2}, {-12, 14, 1},
            {-9, -14, 0}, {-8, -6, 2}, {-9, 3, 1}, {-7, 12, 0},
            {-3, -13, 1}, {-2, -3, 0}, {-3, 5, 2}, {0, 14, 1},
            {5, -13, 2}, {7, -9, 0}, {7, 10, 1}, {11, 11, 0},
            {16, -11, 2}, {19, -7, 1}, {21, 4, 0}, {21, 12, 2}
    };
    private static final int[][] PUDDLE_BASIN_CENTERS = {
            {-19, 1}, {-14, -8}, {-12, 7}, {-5, 0}, {0, -10},
            {1, 10}, {19, -11}, {20, 8}
    };

    @Override public String id() { return "agent-living-cow-herd"; }
    @Override public String title() { return "Living Cow Herd"; }
    @Override public String description() {
        return "Six autonomous Cows share a larger meadow with abundant regrowing plants, sparse rain puddles and a broad interior lake. The scene is intended for observing multi-agent contention, shoreline choice and representative runtime cost.";
    }
    @Override public boolean manualMovementEnabled() { return false; }

    @Override
    public ScenarioSession create() {
        PrecipitationSchedule rainSchedule = PrecipitationSchedule.cyclic(
                RAIN_PULSE_VOLUME,
                1L,
                RAIN_ACTIVE_TICKS,
                CLIMATE_CYCLE_TICKS);

        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(MIN_X, MAX_X, MIN_Y, MAX_Y, -2, 3);
        MaterialDefinitionId meadow = assembly.landscapeDefinition("scenario:herd_meadow");
        MaterialDefinitionId basin = assembly.landscapeDefinition("scenario:herd_puddle_basin");
        MaterialDefinitionId lakeFloor = assembly.landscapeDefinition("scenario:herd_lake_floor");
        ObjectDefinitionId cow = assembly.objectDefinition("scenario:herd_cow");
        ObjectDefinitionId grass = assembly.objectDefinition("scenario:herd_grass");
        ObjectDefinitionId clover = assembly.objectDefinition("scenario:herd_clover");
        ObjectDefinitionId dandelion = assembly.objectDefinition("scenario:herd_dandelion");

        // The visual fixture uses explicit meadow/basin materials. Generated-world spatial Soil
        // differences belong to prepared property fields rather than runtime coordinate hashing.
        assembly.soilProperties(meadow, 6_000, 6_000);
        assembly.surfaceRetention(meadow, 500);
        assembly.soilProperties(basin, 600, 1_000);
        assembly.surfaceRetention(basin, 2_000);
        assembly.surfaceRetention(lakeFloor, 500);
        assembly.precipitation(rainSchedule);
        assembly.periodicEvaporation(EVAPORATION_PER_EVENT, 4L);

        assembly.movementRate(cow, 650);
        assembly.waterWading(cow, COW_MAX_WADING_DEPTH);
        assembly.exclusiveOccupancy(cow);
        assembly.agent(cow, GRAZE);
        assembly.vision(cow, COW_VISION_RANGE, COW_HORIZONTAL_FOV_DEGREES);
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

        List<ObjectId> cows = new ArrayList<>(COW_STARTS.length);
        Map<ObjectId, String> names = new LinkedHashMap<>();
        for (int index = 0; index < COW_STARTS.length; index++) {
            ObjectId cowId = assembly.createObject(cow);
            int[] start = COW_STARTS[index];
            int[] facing = COW_FACING[index];
            assembly.placeObject(cowId, start[0], start[1], STANDING_Z);
            assembly.initialFacing(cowId, facing[0], facing[1]);
            cows.add(cowId);
            names.put(cowId, index == 4 ? "North Shore Cow" : "Cow " + (index + 1));
        }

        ObjectDefinitionId[] plantDefinitions = {grass, clover, dandelion};
        String[] plantNames = {"Grass", "Clover", "Dandelion"};
        for (int[] site : PLANT_SITES) {
            ObjectDefinitionId definition = plantDefinitions[site[2]];
            ObjectId plant = assembly.createObject(definition);
            assembly.placeObject(plant, site[0], site[1], STANDING_Z);
            names.put(plant, plantNames[site[2]]);
        }

        SimulationRuntime runtime = assembly.start();
        ObjectPresentationBindings presentations = new ObjectPresentationBindings(Map.of(
                cow, new ObjectPresentation(
                        "Cow",
                        "Autonomous herbivore balancing Hunger and Thirst through the shared Agent stack.",
                        ObjectVisualFamily.CREATURE,
                        0),
                grass, new ObjectPresentation(
                        "Grass",
                        "Finite regrowing plant biomass.",
                        ObjectVisualFamily.VEGETATION,
                        0),
                clover, new ObjectPresentation(
                        "Clover",
                        "Richer finite regrowing forage on the same mechanics.",
                        ObjectVisualFamily.VEGETATION,
                        1),
                dandelion, new ObjectPresentation(
                        "Dandelion",
                        "Flowering forage with its own stock, growth and use timing.",
                        ObjectVisualFamily.VEGETATION,
                        2)));

        LivingCowController controller = new LivingCowController(
                runtime,
                cows,
                HUNGER,
                THIRST,
                names);
        WeatherPresentationLookup weather = () -> rainSchedule.activeAt(runtime.time().tick())
                ? WeatherPresentation.rain(0.55f)
                : WeatherPresentation.CLEAR;
        return new ScenarioSession(
                runtime,
                new ScenarioView(STANDING_Z, 0f, 0f, 0.42f),
                controller,
                presentations,
                weather);
    }

    static boolean insideLake(int x, int y) {
        long dx = (long) x - LAKE_CENTER_X;
        long dy = (long) y - LAKE_CENTER_Y;
        long radiusX2 = (long) LAKE_RADIUS_X * LAKE_RADIUS_X;
        long radiusY2 = (long) LAKE_RADIUS_Y * LAKE_RADIUS_Y;
        return dx * dx * radiusY2 + dy * dy * radiusX2 <= radiusX2 * radiusY2;
    }

    static boolean isPuddleBasin(int x, int y) {
        if (insideLake(x, y)) return false;
        for (int[] center : PUDDLE_BASIN_CENTERS) {
            int dx = Math.abs(x - center[0]);
            int dy = Math.abs(y - center[1]);
            if (dx + dy <= 1) return true;
        }
        return false;
    }
}
