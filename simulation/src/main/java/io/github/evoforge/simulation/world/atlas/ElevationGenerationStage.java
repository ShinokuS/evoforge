package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Deterministic smooth elevation with legacy v1 and precise v2+ output semantics. */
public final class ElevationGenerationStage implements ElevationGenerator {
    public static final GenerationStageId STAGE_ID = GenerationStageId.of("world:elevation");

    private static final GenerationPurposeId COARSE = GenerationPurposeId.of("world:coarse");
    private static final GenerationPurposeId MEDIUM = GenerationPurposeId.of("world:medium");
    private static final GenerationPurposeId DETAIL = GenerationPurposeId.of("world:detail");
    private static final int SAMPLE_MAX = 65_535;

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) {
            throw new IllegalArgumentException("genesis must not be null");
        }
        GenerationRevision revision = genesis.generationRevision();
        if (!GenerationRevision.V1.equals(revision)
                && !GenerationRevision.V2.equals(revision)
                && !GenerationRevision.V3.equals(revision)
                && !GenerationRevision.V4.equals(revision)
                && !GenerationRevision.V5.equals(revision)
                && !GenerationRevision.V6.equals(revision)
                && !GenerationRevision.V7.equals(revision)
                && !GenerationRevision.V8.equals(revision)) {
            throw new IllegalArgumentException(
                    "unsupported generation revision: " + revision.value());
        }

        WorldBounds bounds = genesis.spec().bounds();
        long[] elevations = new long[DenseElevationField.cellCount(bounds)];
        GenerationRandom random = GenerationRandom.from(genesis);
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        boolean precise = !GenerationRevision.V1.equals(revision);

        int index = 0;
        for (long localY = 0; localY < height; localY++) {
            int y = (int) ((long) bounds.minY() + localY);
            for (long localX = 0; localX < width; localX++) {
                int x = (int) ((long) bounds.minX() + localX);
                elevations[index++] = elevationSubunitsAt(
                        random,
                        bounds,
                        x,
                        y,
                        precise);
            }
        }
        return new DenseElevationField(bounds, elevations);
    }

    private static long elevationSubunitsAt(
            GenerationRandom random,
            WorldBounds bounds,
            int x,
            int y,
            boolean precise) {
        int coarse = valueNoise(random, COARSE, x, y, 32);
        int medium = valueNoise(random, MEDIUM, x, y, 16);
        int detail = valueNoise(random, DETAIL, x, y, 8);
        int normalized = (coarse * 4 + medium * 2 + detail) / 7;

        long verticalSpan = (long) bounds.maxZ() - bounds.minZ();
        long surfaceMin = (long) bounds.minZ() + verticalSpan / 4L;
        long surfaceMax = (long) bounds.minZ() + (verticalSpan * 3L) / 4L;
        long surfaceSpan = surfaceMax - surfaceMin;
        long numerator = (long) normalized * surfaceSpan;
        long wholeCells = numerator / SAMPLE_MAX;
        long discreteElevation = surfaceMin + wholeCells;
        long discreteSubunits = Math.multiplyExact(
                discreteElevation,
                ElevationField.SUBUNITS_PER_CELL);

        if (!precise) {
            return discreteSubunits;
        }

        long remainder = numerator % SAMPLE_MAX;
        long fractionalSubunits = (remainder * ElevationField.SUBUNITS_PER_CELL)
                / SAMPLE_MAX;
        return Math.addExact(discreteSubunits, fractionalSubunits);
    }

    private static int valueNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            int x,
            int y,
            int scale) {
        long latticeX = Math.floorDiv((long) x, scale);
        long latticeY = Math.floorDiv((long) y, scale);
        int offsetX = (int) Math.floorMod((long) x, scale);
        int offsetY = (int) Math.floorMod((long) y, scale);

        int lowerLeft = sample(random, purpose, latticeX, latticeY);
        int lowerRight = sample(random, purpose, latticeX + 1L, latticeY);
        int upperLeft = sample(random, purpose, latticeX, latticeY + 1L);
        int upperRight = sample(random, purpose, latticeX + 1L, latticeY + 1L);

        int lower = interpolate(lowerLeft, lowerRight, offsetX, scale);
        int upper = interpolate(upperLeft, upperRight, offsetX, scale);
        return interpolate(lower, upper, offsetY, scale);
    }

    private static int sample(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long latticeX,
            long latticeY) {
        return (int) ((random.sampleLong(
                STAGE_ID,
                purpose,
                latticeX,
                latticeY,
                0L,
                0L) >>> 48) & SAMPLE_MAX);
    }

    private static int interpolate(int from, int to, int offset, int scale) {
        return (int) (((long) from * (scale - offset) + (long) to * offset) / scale);
    }
}
