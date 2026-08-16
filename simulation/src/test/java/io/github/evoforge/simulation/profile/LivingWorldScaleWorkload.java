package io.github.evoforge.simulation.profile;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.agent.CapabilityId;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.interaction.InteractionReachProfiles;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Repeatable headless workload for measuring the current living-world baseline.
 *
 * <p>The workload deliberately uses only production {@link SimulationAssembly}
 * and {@link SimulationRuntime} contracts. Profile size is test infrastructure,
 * not a world-size or chunking contract.
 */
final class LivingWorldScaleWorkload {
    static final String PROFILE_PROPERTY = "evoforge.scale.profile";

    private static final NeedId HUNGER = NeedId.of("profile:hunger");
    private static final NeedId THIRST = NeedId.of("profile:thirst");
    private static final CapabilityId GRAZE = CapabilityId.of("profile:graze");
    private static final int LANE_MIN_X = -6;
    private static final int LANE_MAX_X = 6;
    private static final int LANE_SPACING = 16;
    private static final int WATER_X = -5;
    private static final int GRASS_X = 5;

    private LivingWorldScaleWorkload() {}

    static RunResult run(Profile profile) {
        return run(profile.name().toLowerCase(), profile.agents(), profile.ticks());
    }

    static RunResult run(String name, int agentCount, int ticks) {
        if (agentCount <= 0) throw new IllegalArgumentException("agentCount must be > 0");
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");

        long heapBefore = usedHeapBytes();
        long setupStarted = System.nanoTime();
        Workload workload = create(agentCount);
        long setupNanos = System.nanoTime() - setupStarted;
        long heapAfterSetup = usedHeapBytes();

        long runStarted = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            workload.runtime().stepper().advance();
        }
        long runNanos = System.nanoTime() - runStarted;
        long heapAfterRun = usedHeapBytes();

        Snapshot snapshot = snapshot(workload);
        return new RunResult(
                name,
                agentCount,
                ticks,
                setupNanos,
                runNanos,
                heapBefore,
                heapAfterSetup,
                heapAfterRun,
                snapshot);
    }

    static Profile profile(String value) {
        String normalized = value == null ? "medium" : value.trim().toLowerCase();
        return switch (normalized) {
            case "small" -> Profile.SMALL;
            case "medium" -> Profile.MEDIUM;
            case "large" -> Profile.LARGE;
            case "stress" -> Profile.STRESS;
            default -> throw new IllegalArgumentException(
                    "unknown scale profile '" + value + "'; expected small, medium, large, or stress");
        };
    }

    private static Workload create(int agentCount) {
        int maxY = (agentCount - 1) * LANE_SPACING;
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(LANE_MIN_X - 1, LANE_MAX_X + 1, -1, maxY + 1, -1, 3);

        LandscapeDefinitionId ground = assembly.landscapeDefinition("profile:ground");
        assembly.surfaceRetention(ground, 500_000);

        ObjectDefinitionId cowDefinition = assembly.objectDefinition("profile:cow");
        ObjectDefinitionId grassDefinition = assembly.objectDefinition("profile:grass");

        assembly.movementRate(cowDefinition, 1_000);
        assembly.exclusiveOccupancy(cowDefinition);
        assembly.agent(cowDefinition, GRAZE);
        assembly.vision(cowDefinition, 6, 360);
        assembly.need(cowDefinition, HUNGER, 100, 35);
        assembly.need(cowDefinition, THIRST, 100, 45);
        assembly.needMotivation(cowDefinition, HUNGER, 10);
        assembly.needMotivation(cowDefinition, THIRST, 10);
        assembly.needProgression(cowDefinition, HUNGER, 4, 7);
        assembly.needProgression(cowDefinition, THIRST, 5, 5);
        assembly.knowsNeedSolution(cowDefinition, HUNGER);
        assembly.knowsNeedSolution(cowDefinition, THIRST);

        assembly.consumableStock(grassDefinition, 8, 4);
        assembly.growth(grassDefinition, 1, 12);
        assembly.satisfiesNeed(grassDefinition, HUNGER, 35, 1, 2, GRAZE);

        assembly.physicalCellVolumeMilliliters(1_000_000L);
        assembly.drinksLiquid(
                cowDefinition,
                THIRST,
                WaterSystem.TYPE,
                10_000L,
                50L,
                2L,
                InteractionReachProfiles.cardinalSameOrOneBelow());

        ObjectId[] cows = new ObjectId[agentCount];
        ObjectId[] grasses = new ObjectId[agentCount];
        int[] laneYs = new int[agentCount];

        for (int index = 0; index < agentCount; index++) {
            int y = index * LANE_SPACING;
            laneYs[index] = y;
            for (int x = LANE_MIN_X; x <= LANE_MAX_X; x++) {
                assembly.placeTerrain(x, y, 0, ground);
            }
            assembly.initialWater(WATER_X, y, 1, 200_000);

            ObjectId grass = assembly.createObject(grassDefinition);
            ObjectId cow = assembly.createObject(cowDefinition);
            assembly.placeObject(grass, GRASS_X, y, 1);
            assembly.placeObject(cow, 0, y, 1);
            assembly.initialFacing(cow, index % 2 == 0 ? 1 : -1, 0);
            grasses[index] = grass;
            cows[index] = cow;
        }

        return new Workload(assembly.start(), cows, grasses, laneYs);
    }

    private static Snapshot snapshot(Workload workload) {
        SimulationRuntime runtime = workload.runtime();
        StringBuilder canonical = new StringBuilder();
        long hungerTotal = 0;
        long thirstTotal = 0;
        long plantStockTotal = 0;
        long waterTotal = 0;

        canonical.append("tick=").append(runtime.time().tick()).append(';');
        for (int index = 0; index < workload.cows().length; index++) {
            ObjectId cow = workload.cows()[index];
            ObjectId grass = workload.grasses()[index];
            int y = workload.laneYs()[index];
            long hunger = runtime.view().needs().level(cow, HUNGER);
            long thirst = runtime.view().needs().level(cow, THIRST);
            long stock = runtime.view().consumableStocks().quantity(grass);
            long water = runtime.view().water().amount(WATER_X, y, 1);

            hungerTotal += hunger;
            thirstTotal += thirst;
            plantStockTotal += stock;
            waterTotal += water;

            canonical.append(cow.asLong()).append(':')
                    .append(runtime.view().transforms().x(cow)).append(',')
                    .append(runtime.view().transforms().y(cow)).append(',')
                    .append(runtime.view().transforms().z(cow)).append(',')
                    .append(hunger).append(',')
                    .append(thirst).append(',')
                    .append(stock).append(',')
                    .append(water).append(';');
        }

        return new Snapshot(
                runtime.time().tick(),
                sha256(canonical.toString()),
                hungerTotal,
                thirstTotal,
                plantStockTotal,
                waterTotal);
    }

    private static String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte item : hash) result.append(String.format("%02x", item & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static long usedHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    enum Profile {
        SMALL(8, 250),
        MEDIUM(32, 500),
        LARGE(128, 500),
        STRESS(512, 1_000);

        private final int agents;
        private final int ticks;

        Profile(int agents, int ticks) {
            this.agents = agents;
            this.ticks = ticks;
        }

        int agents() {
            return agents;
        }

        int ticks() {
            return ticks;
        }
    }

    record Snapshot(
            long tick,
            String fingerprint,
            long hungerTotal,
            long thirstTotal,
            long plantStockTotal,
            long waterTotal) {}

    record RunResult(
            String name,
            int agents,
            int ticks,
            long setupNanos,
            long runNanos,
            long heapBeforeBytes,
            long heapAfterSetupBytes,
            long heapAfterRunBytes,
            Snapshot snapshot) {

        String report() {
            return "scale-profile"
                    + " name=" + name
                    + " agents=" + agents
                    + " ticks=" + ticks
                    + " setupMs=" + nanosToMillis(setupNanos)
                    + " runMs=" + nanosToMillis(runNanos)
                    + " nsPerAgentTick=" + nanosPerAgentTick()
                    + " heapBeforeBytes=" + heapBeforeBytes
                    + " heapAfterSetupBytes=" + heapAfterSetupBytes
                    + " heapAfterRunBytes=" + heapAfterRunBytes
                    + " fingerprint=" + snapshot.fingerprint()
                    + " hungerTotal=" + snapshot.hungerTotal()
                    + " thirstTotal=" + snapshot.thirstTotal()
                    + " plantStockTotal=" + snapshot.plantStockTotal()
                    + " waterTotal=" + snapshot.waterTotal();
        }

        private long nanosPerAgentTick() {
            long workUnits = (long) agents * ticks;
            return workUnits == 0 ? 0 : runNanos / workUnits;
        }

        private static long nanosToMillis(long nanos) {
            return nanos / 1_000_000L;
        }
    }

    private record Workload(
            SimulationRuntime runtime,
            ObjectId[] cows,
            ObjectId[] grasses,
            int[] laneYs) {}
}
