package io.github.evoforge.simulation.profile;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathQuery;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearch;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathSearchStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Repeatable pathfinding-only workload over the production hierarchical pathfinder. */
final class NavigationScaleWorkload {

    private static final int SEARCH_SLICE_BUDGET = 512;

    private NavigationScaleWorkload() {}

    static RunResult run(Profile profile) {
        return run(profile.name().toLowerCase(), profile.side(), profile.queries());
    }

    static RunResult run(String name, int side, int queryCount) {
        if (side < 8) throw new IllegalArgumentException("side must be >= 8");
        if (queryCount <= 0) throw new IllegalArgumentException("queryCount must be > 0");

        long heapBefore = usedHeapBytes();
        long setupStarted = System.nanoTime();
        SimulationRuntime runtime = create(side);
        long setupNanos = System.nanoTime() - setupStarted;
        long heapAfterSetup = usedHeapBytes();

        StringBuilder canonical = new StringBuilder(queryCount * side * 12);
        long searchNanos = 0;
        long totalCostUnits = 0;
        long totalRouteSteps = 0;
        int found = 0;

        for (int index = 0; index < queryCount; index++) {
            int startY = 1 + Math.floorMod(index * 7, side - 2);
            int goalY = 1 + Math.floorMod(index * 11 + 3, side - 2);
            PathSearch search = runtime.view().pathfinder().begin(
                    PathQuery.between(0, startY, 0, side - 1, goalY, 0));

            long searchStarted = System.nanoTime();
            complete(search);
            searchNanos += System.nanoTime() - searchStarted;

            canonical.append(index).append(':').append(search.status()).append(':');
            if (search.status() == PathSearchStatus.FOUND) {
                found++;
                PathRoute route = search.route();
                totalCostUnits += route.totalCostUnits();
                totalRouteSteps += route.size();
                canonical.append(route.totalCostUnits()).append(':').append(route.size()).append(':');
                for (int step = 0; step < route.size(); step++) {
                    canonical.append(route.x(step)).append(',')
                            .append(route.y(step)).append(',')
                            .append(route.z(step)).append('|');
                }
            }
            canonical.append(';');
        }
        long heapAfterRun = usedHeapBytes();

        Snapshot snapshot = new Snapshot(
                sha256(canonical.toString()),
                found,
                totalRouteSteps,
                totalCostUnits);
        return new RunResult(
                name,
                side,
                queryCount,
                setupNanos,
                searchNanos,
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
                .worldBounds(0, side - 1, 0, side - 1, -1, 1);
        LandscapeDefinitionId ground = assembly.landscapeDefinition("profile:navigation-ground");

        for (int x = 0; x < side; x++) {
            boolean wallColumn = x >= 2 && x <= side - 3 && Math.floorMod(x - 2, 4) == 0;
            int wallOrdinal = wallColumn ? (x - 2) / 4 : -1;
            int gateY = (wallOrdinal & 1) == 0 ? 1 : side - 2;
            for (int y = 0; y < side; y++) {
                if (!wallColumn || y == gateY) {
                    assembly.placeTerrain(x, y, -1, ground);
                }
            }
        }

        return assembly.start();
    }

    private static void complete(PathSearch search) {
        while (search.status() == PathSearchStatus.RUNNING) {
            search.advance(SEARCH_SLICE_BUDGET);
        }
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
        SMALL(16, 16),
        MEDIUM(32, 64),
        LARGE(64, 128),
        STRESS(96, 256);

        private final int side;
        private final int queries;

        Profile(int side, int queries) {
            this.side = side;
            this.queries = queries;
        }

        int side() {
            return side;
        }

        int queries() {
            return queries;
        }
    }

    record Snapshot(
            String fingerprint,
            int foundQueries,
            long totalRouteSteps,
            long totalCostUnits) {}

    record RunResult(
            String name,
            int side,
            int queries,
            long setupNanos,
            long searchNanos,
            long heapBeforeBytes,
            long heapAfterSetupBytes,
            long heapAfterRunBytes,
            Snapshot snapshot) {

        String report() {
            return "navigation-scale-profile"
                    + " name=" + name
                    + " side=" + side
                    + " supportEnvelopeCells=" + ((long) side * side)
                    + " queries=" + queries
                    + " setupMs=" + nanosToMillis(setupNanos)
                    + " searchMs=" + nanosToMillis(searchNanos)
                    + " nsPerQuery=" + nanosPerQuery()
                    + " heapBeforeBytes=" + heapBeforeBytes
                    + " heapAfterSetupBytes=" + heapAfterSetupBytes
                    + " heapAfterRunBytes=" + heapAfterRunBytes
                    + " fingerprint=" + snapshot.fingerprint()
                    + " foundQueries=" + snapshot.foundQueries()
                    + " totalRouteSteps=" + snapshot.totalRouteSteps()
                    + " totalCostUnits=" + snapshot.totalCostUnits();
        }

        private long nanosPerQuery() {
            return queries == 0 ? 0 : searchNanos / queries;
        }

        private static long nanosToMillis(long nanos) {
            return nanos / 1_000_000L;
        }
    }
}
