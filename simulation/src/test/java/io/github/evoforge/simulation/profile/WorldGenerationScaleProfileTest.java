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

/** Manual end-to-end scale profile for the deterministic generated-world pipeline. */
final class WorldGenerationScaleProfileTest {
    private static final String SIDE_PROPERTY = "evoforge.worldgen.profile.side";
    private static final long SEED = 4_859_186_304_997_574_751L;
    private static final int LAND_COVERAGE_PPM = 830_000;
    private static final int SAMPLE_COUNT = 4_096;

    @Test
    @Tag("worldgen-scale-profile")
    void profileGeneratedWorldStages() {
        int side = Integer.parseInt(System.getProperty(SIDE_PROPERTY, "512"));
        if (side < 32) {
            throw new IllegalArgumentException("worldgen profile side must be >= 32");
        }
        WorldGenesis genesis = genesis(side);
        WorldGenerationAlgorithms algorithms = WorldGenerationAlgorithms.standard();

        Runtime runtime = Runtime.getRuntime();
        long beforeBytes = usedHeap(runtime);

        long start = System.nanoTime();
        var elevation = algorithms.elevation().generate(genesis);
        long afterElevation = System.nanoTime();
        long elevationBytes = usedHeap(runtime);

        var geology = algorithms.geology().generate(genesis);
        long afterGeology = System.nanoTime();
        long geologyBytes = usedHeap(runtime);

        var climate = algorithms.climate().generate(genesis, elevation);
        long afterClimate = System.nanoTime();
        long climateBytes = usedHeap(runtime);

        var drainage = algorithms.drainage().generate(elevation);
        long afterDrainage = System.nanoTime();
        long drainageBytes = usedHeap(runtime);

        var hydrography = algorithms.hydrography().generate(genesis, elevation, drainage);
        long afterHydrography = System.nanoTime();
        long hydrographyBytes = usedHeap(runtime);

        var surfaceHydrology = algorithms.surfaceHydrology().generate(
                genesis, elevation, drainage, hydrography, climate);
        long end = System.nanoTime();
        long finalBytes = usedHeap(runtime);

        long checksum = sampledChecksum(
                side, elevation, drainage, hydrography, surfaceHydrology);
        assertTrue(checksum != 0L, "profile checksum must exercise generated facts");
        assertTrue(geology.bounds().equals(genesis.spec().bounds()));

        System.out.printf(Locale.ROOT,
                "WORLDGEN_PROFILE side=%d cells=%d seed=%d%n"
                        + "  elevation_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  geology_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  climate_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  drainage_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  hydrography_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  surface_hydrology_ms=%.3f retained_delta_mib=%.2f%n"
                        + "  total_ms=%.3f retained_total_mib=%.2f checksum=%016x%n",
                side,
                Math.multiplyExact(side, side),
                SEED,
                millis(start, afterElevation), mib(elevationBytes - beforeBytes),
                millis(afterElevation, afterGeology), mib(geologyBytes - elevationBytes),
                millis(afterGeology, afterClimate), mib(climateBytes - geologyBytes),
                millis(afterClimate, afterDrainage), mib(drainageBytes - climateBytes),
                millis(afterDrainage, afterHydrography), mib(hydrographyBytes - drainageBytes),
                millis(afterHydrography, end), mib(finalBytes - hydrographyBytes),
                millis(start, end), mib(finalBytes - beforeBytes), checksum);
    }

    private static long sampledChecksum(
            int side,
            io.github.evoforge.simulation.world.atlas.ElevationField elevation,
            io.github.evoforge.simulation.world.atlas.DrainageField drainage,
            io.github.evoforge.simulation.world.atlas.HydrographyField hydrography,
            io.github.evoforge.simulation.world.atlas.SurfaceHydrologyField surfaceHydrology) {
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
            if (hydrography.isChannelAt(x, y)) state ^= 0x94d049bb133111ebL;
            state = mix64(state ^ surfaceHydrology.initialWaterVolumeAt(x, y));
            if (surfaceHydrology.isShoreline(x, y)) state ^= 0xbf58476d1ce4e5b9L;
        }
        return state;
    }

    private static WorldGenesis genesis(int side) {
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
                GenerationRevision.V15,
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
