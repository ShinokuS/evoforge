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

final class SmoothValueNoiseRowSamplerTest {
    private static final int SAMPLE_MAX = 65_535;
    private static final int PPM = NormalizedValue.SCALE;

    @Test
    void matchesDirectFourCornerSamplingAcrossNegativeCoordinatesAndScaleTransitions() {
        WorldGenesis genesis = genesis();
        GenerationRandom.BoundSampler random = GenerationRandom.from(genesis).bind(
                ElevationGenerationStage.STAGE_ID,
                GenerationPurposeId.of("test:row-cached-noise"));
        int[] scales = {1, 2, 3, 7, 16, 31};

        for (int scale : scales) {
            SmoothValueNoiseRowSampler cached = new SmoothValueNoiseRowSampler(
                    random,
                    genesis.spec().bounds().minX(),
                    genesis.spec().bounds().maxX(),
                    scale);
            for (int y = genesis.spec().bounds().minY(); y <= genesis.spec().bounds().maxY(); y++) {
                for (int x = genesis.spec().bounds().minX(); x <= genesis.spec().bounds().maxX(); x++) {
                    assertEquals(
                            direct(random, x, y, scale),
                            cached.sampleAt(x, y),
                            "cached value noise changed at " + x + "," + y + " scale=" + scale);
                }
            }
        }
    }

    private static int direct(
            GenerationRandom.BoundSampler random,
            int x,
            int y,
            int scale) {
        long latticeX = Math.floorDiv((long) x, scale);
        long latticeY = Math.floorDiv((long) y, scale);
        int offsetX = (int) Math.floorMod((long) x, scale);
        int offsetY = (int) Math.floorMod((long) y, scale);
        int lowerLeft = sample(random, latticeX, latticeY);
        int lowerRight = sample(random, latticeX + 1L, latticeY);
        int upperLeft = sample(random, latticeX, latticeY + 1L);
        int upperRight = sample(random, latticeX + 1L, latticeY + 1L);
        int lower = smoothInterpolate(lowerLeft, lowerRight, offsetX, scale);
        int upper = smoothInterpolate(upperLeft, upperRight, offsetX, scale);
        return smoothInterpolate(lower, upper, offsetY, scale);
    }

    private static int sample(
            GenerationRandom.BoundSampler random,
            long latticeX,
            long latticeY) {
        return (int) ((random.sampleLong(latticeX, latticeY, 0L, 0L) >>> 48) & SAMPLE_MAX);
    }

    private static int smoothInterpolate(int from, int to, int offset, int scale) {
        long coordinate = ((long) offset * PPM) / scale;
        int fade = smoothStepPpm(coordinate);
        return (int) (((long) from * (PPM - fade) + (long) to * fade) / PPM);
    }

    private static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) PPM, coordinatePpm));
        long coordinateSquared = coordinate * coordinate;
        return (int) (coordinateSquared
                * (3L * PPM - 2L * coordinate)
                / ((long) PPM * PPM));
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
