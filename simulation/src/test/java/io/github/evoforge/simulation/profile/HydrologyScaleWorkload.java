package io.github.evoforge.simulation.profile;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.material.MaterialDefinitionId;
import io.github.evoforge.simulation.world.liquid.water.WaterSystem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Repeatable hydrology-only workload over the production runtime. */
final class HydrologyScaleWorkload {

    private HydrologyScaleWorkload() {}

    static RunResult run(Profile profile) {
        return run(profile.name().toLowerCase(), profile.side(), profile.ticks());
    }

    static RunResult run(String name, int side, int ticks) {
        if (side < 2) throw new IllegalArgumentException("side must be >= 2");
        if (ticks < 0) throw new IllegalArgumentException("ticks must be >= 0");

        long heapBefore = usedHeapBytes();
        long setupStarted = System.nanoTime();
        SimulationRuntime runtime = create(side);
        long setupNanos = System.nanoTime() - setupStarted;
        long heapAfterSetup = usedHeapBytes();

        long runStarted = System.nanoTime();
        for (int tick = 0; tick < ticks; tick++) {
            runtime.stepper().advance();
        }
        long runNanos = System.nanoTime() - runStarted;
        long heapAfterRun = usedHeapBytes();

        Snapshot snapshot = snapshot(runtime, side);
        return new RunResult(
                name,
                side,
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

    private static SimulationRuntime create(int side) {
        SimulationAssembly assembly = SimulationAssembly.create()
                .worldBounds(0, side - 1, 0, side - 1, 0, 2);
        MaterialDefinitionId soil = assembly.landscapeDefinition("profile:hydrology-soil");

        assembly.soilProperties(soil, 250_000, 20_000);
        assembly.periodicPrecipitation(20_000, 5L);
        assembly.periodicEvaporation(10_000, 7L);

        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                assembly.placeTerrain(x, y, 0, soil);
                if (((x + y) & 3) == 0) {
                    assembly.initialWater(x, y, 1, 600_000);
                }
            }
        }

        return assembly.start();
    }

    private static Snapshot snapshot(SimulationRuntime runtime, int side) {
        StringBuilder canonical = new StringBuilder(side * side * 24);
        long retainedWater = 0;
        long freeWater = 0;
        int wetColumns = 0;

        canonical.append("tick=").append(runtime.time().tick()).append(';');
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                long retained = runtime.view().soilLiquids().amountOf(WaterSystem.TYPE, x, y, 0);
                long free = runtime.view().water().amount(x, y, 1)
                        + runtime.view().water().amount(x, y, 2);
                retainedWater += retained;
                freeWater += free;
                if (retained + free > 0) wetColumns++;
                canonical.append(x).append(',').append(y).append(':')
                        .append(retained).append(',').append(free).append(';');
            }
        }

        return new Snapshot(
                runtime.time().tick(),
                sha256(canonical.toString()),
                retainedWater,
                freeWater,
                wetColumns);
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
        SMALL(8, 120),
        MEDIUM(16, 250),
        LARGE(32, 250),
        STRESS(48, 500);

        private final int side;
        private final int ticks;

        Profile(int side, int ticks) {
            this.side = side;
            this.ticks = ticks;
        }

        int side() {
            return side;
        }

        int ticks() {
            return ticks;
        }
    }

    record Snapshot(
            long tick,
            String fingerprint,
            long retainedWater,
            long freeWater,
            int wetColumns) {}

    record RunResult(
            String name,
            int side,
            int ticks,
            long setupNanos,
            long runNanos,
            long heapBeforeBytes,
            long heapAfterSetupBytes,
            long heapAfterRunBytes,
            Snapshot snapshot) {

        String report() {
            return "hydrology-scale-profile"
                    + " name=" + name
                    + " side=" + side
                    + " cells=" + ((long) side * side)
                    + " ticks=" + ticks
                    + " setupMs=" + nanosToMillis(setupNanos)
                    + " runMs=" + nanosToMillis(runNanos)
                    + " nsPerCellTick=" + nanosPerCellTick()
                    + " heapBeforeBytes=" + heapBeforeBytes
                    + " heapAfterSetupBytes=" + heapAfterSetupBytes
                    + " heapAfterRunBytes=" + heapAfterRunBytes
                    + " fingerprint=" + snapshot.fingerprint()
                    + " retainedWater=" + snapshot.retainedWater()
                    + " freeWater=" + snapshot.freeWater()
                    + " wetColumns=" + snapshot.wetColumns();
        }

        private long nanosPerCellTick() {
            long workUnits = (long) side * side * ticks;
            return workUnits == 0 ? 0 : runNanos / workUnits;
        }

        private static long nanosToMillis(long nanos) {
            return nanos / 1_000_000L;
        }
    }
}
