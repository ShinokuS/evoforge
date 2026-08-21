package io.github.evoforge.simulation.profile;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.atlas.WorldGenerationAlgorithms;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Locale;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Manual scale profile for the currently supported V15 terrain and drainage pipeline. */
final class WorldGenerationScaleProfileTest {
    private static final String SIDE_PROPERTY = "evoforge.worldgen.profile.side";
    private static final long SEED = 4_859_186_304_997_574_751L;
    private static final int LAND_COVERAGE_PPM = 830_000;
    private static final int SAMPLE_COUNT = 4_096;
    private static final int BREAKDOWN_SIDE = 512;

    @Test
    @Tag("worldgen-scale-profile")
    void profileV15TerrainAndDrainage() {
        int side = Integer.parseInt(System.getProperty(SIDE_PROPERTY, "512"));
        if (side < 32) {
            throw new IllegalArgumentException("worldgen profile side must be >= 32");
        }
        WorldGenerationAlgorithms algorithms = WorldGenerationAlgorithms.standard();
        WorldGenesis genesis = genesis(side, GenerationRevision.V15);

        Runtime runtime = Runtime.getRuntime();
        long beforeBytes = usedHeap(runtime);

        long start = System.nanoTime();
        var elevation = algorithms.elevation().generate(genesis);
        long afterElevation = System.nanoTime();
        long elevationBytes = usedHeap(runtime);

        var drainage = algorithms.drainage().generate(elevation);
        long end = System.nanoTime();
        long finalBytes = usedHeap(runtime);

        long checksum = sampledChecksum(side, elevation, drainage);
        assertTrue(checksum != 0L, "profile checksum must exercise generated facts");

        System.out.printf(Locale.ROOT,
                "WORLDGEN_PROFILE revision=V15 side=%d cells=%d seed=%d%n"
                        + "  elevation_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  drainage_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  total_ms=%.3f retained_total_mib=%.2f checksum=%016x%n",
                side,
                Math.multiplyExact(side, side),
                SEED,
                millis(start, afterElevation), mib(elevationBytes - beforeBytes),
                millis(afterElevation, end), mib(finalBytes - elevationBytes),
                millis(start, end), mib(finalBytes - beforeBytes), checksum);

        profileElevationRevisionBreakdown(Math.min(BREAKDOWN_SIDE, side), algorithms);
    }

    private static void profileElevationRevisionBreakdown(
            int side,
            WorldGenerationAlgorithms algorithms) {
        System.out.printf(Locale.ROOT,
                "WORLDGEN_REVISION_BREAKDOWN side=%d cells=%d seed=%d%n",
                side,
                Math.multiplyExact(side, side),
                SEED);
        for (GenerationRevision revision : new GenerationRevision[] {
                GenerationRevision.V12,
                GenerationRevision.V13,
                GenerationRevision.V14,
                GenerationRevision.V15
        }) {
            WorldGenesis genesis = genesis(side, revision);
            long start = System.nanoTime();
            var elevation = algorithms.elevation().generate(genesis);
            long end = System.nanoTime();
            long checksum = sampledElevationChecksum(side, elevation);
            System.out.printf(Locale.ROOT,
                    "  revision=%s elevation_ms=%.3f checksum=%016x%n",
                    revision,
                    millis(start, end),
                    checksum);
        }
    }

    private static long sampledChecksum(
            int side,
            io.github.evoforge.simulation.world.atlas.ElevationField elevation,
            io.github.evoforge.simulation.world.atlas.DrainageField drainage) {
        WorldBounds bounds = elevation.bounds();
        int area = Math.multiplyExact(side, side);
        int samples = Math.min(SAMPLE_COUNT, area);
        long state = 0x9e3779b97f4a7c15L;
        for (int sample = 0; sample < samples; sample++) {
            long mixed = mix64(SEED + sample * 0x9e3779b97f4a7c15L);
            int index = (int) Long.remainderUnsigned(mixed, area);
            int localY = index / side;
            int localX = index - localY * side;
            int x = bounds.minX() + localX;
            int y = bounds.minY() + localY;

            state = mix64(state ^ elevation.elevationSubunitsAt(x, y));
            state = mix64(state ^ drainage.contributingAreaAt(x, y));
            state = mix64(state ^ drainage.terminalXAt(x, y));
            state = mix64(state ^ drainage.terminalYAt(x, y));
        }
        return state;
    }

    private static long sampledElevationChecksum(
            int side,
            io.github.evoforge.simulation.world.atlas.ElevationField elevation) {
        WorldBounds bounds = elevation.bounds();
        int area = Math.multiplyExact(side, side);
        int samples = Math.min(SAMPLE_COUNT, area);
        long state = 0x243f6a8885a308d3L;
        for (int sample = 0; sample < samples; sample++) {
            long mixed = mix64(SEED ^ (sample * 0x9e3779b97f4a7c15L));
            int index = (int) Long.remainderUnsigned(mixed, area);
            int localY = index / side;
            int localX = index - localY * side;
            int x = bounds.minX() + localX;
            int y = bounds.minY() + localY;
            state = mix64(state ^ elevation.elevationSubunitsAt(x, y));
        }
        return state;
    }

    private static WorldGenesis genesis(int side, GenerationRevision revision) {
        int min = -side / 2;
        WorldBounds bounds = new WorldBounds(
                min, min + side - 1,
                min, min + side - 1,
                -96, 96);
        WorldGenerationIntent balanced = WorldGenerationIntent.balanced();
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(LAND_COVERAGE_PPM),
                NormalizedValue.ofPartsPerMillion(750_000),
                NormalizedValue.ofPartsPerMillion(120_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                NormalizedValue.ofPartsPerMillion(450_000),
                balanced.landformScale(),
                balanced.ruggedness(),
                balanced.mountains());
        return new WorldGenesis(
                new WorldSpec(bounds),
                SEED,
                revision,
                RngRevision.V1,
                intent);
    }

    private static long usedHeap(Runtime runtime) {
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static double millis(long from, long to) {
        return (to - from) / 1_000_000d;
    }

    private static double mib(long bytes) {
        return bytes / (1024d * 1024d);
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
