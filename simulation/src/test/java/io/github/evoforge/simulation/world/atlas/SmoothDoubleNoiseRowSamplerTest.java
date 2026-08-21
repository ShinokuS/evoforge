package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class SmoothDoubleNoiseRowSamplerTest {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;

    @Test
    void matchesDirectLandmassNoiseBitForBitAcrossNegativeCoordinatesAndFractionalScales() {
        WorldGenesis genesis = genesis();
        GenerationRandom.BoundSampler random = GenerationRandom.from(genesis).bind(
                ElevationGenerationStage.STAGE_ID,
                GenerationPurposeId.of("test:double-row-cached-noise"));
        double[] scales = {3d, 7.3d, 16.5d, 31.75d, 84.125d};

        for (double scale : scales) {
            SmoothDoubleNoiseRowSampler cached = new SmoothDoubleNoiseRowSampler(
                    random,
                    genesis.spec().bounds().minX(),
                    genesis.spec().bounds().maxX(),
                    scale);
            for (int y = genesis.spec().bounds().minY(); y <= genesis.spec().bounds().maxY(); y++) {
                for (int x = genesis.spec().bounds().minX(); x <= genesis.spec().bounds().maxX(); x++) {
                    double expected = direct(random, x, y, scale);
                    double actual = cached.sampleAt(x, y);
                    assertEquals(
                            Double.doubleToLongBits(expected),
                            Double.doubleToLongBits(actual),
                            "cached double noise changed at " + x + "," + y + " scale=" + scale);
                }
            }
        }
    }

    private static double direct(
            GenerationRandom.BoundSampler random,
            int x,
            int y,
            double scale) {
        double gridX = x / scale;
        double gridY = y / scale;
        long x0 = (long) StrictMath.floor(gridX);
        long y0 = (long) StrictMath.floor(gridY);
        double tx = smooth(gridX - x0);
        double ty = smooth(gridY - y0);
        double a = centeredUnit(random, x0, y0);
        double b = centeredUnit(random, x0 + 1L, y0);
        double c = centeredUnit(random, x0, y0 + 1L);
        double d = centeredUnit(random, x0 + 1L, y0 + 1L);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    private static double centeredUnit(
            GenerationRandom.BoundSampler random,
            long latticeX,
            long latticeY) {
        int sample = (int) ((random.sampleLong(latticeX, latticeY, 0L, 0L) >>> 48) & SAMPLE_MAX);
        int ppm = (int) ((long) sample * PPM / SAMPLE_MAX);
        return ppm / (double) PPM * 2d - 1d;
    }

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }

    private static WorldGenesis genesis() {
        WorldBounds bounds = new WorldBounds(-37, 42, -29, 34, -32, 32);
        return new WorldGenesis(
                new WorldSpec(bounds),
                91_337L,
                GenerationRevision.V15,
                RngRevision.V1,
                WorldGenerationIntent.balanced());
    }
}
