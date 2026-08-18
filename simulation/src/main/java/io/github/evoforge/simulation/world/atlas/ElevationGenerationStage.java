package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/** Deterministic elevation generation; V9 adds oceans, V10 macro relief, V11 organic morphology. */
public final class ElevationGenerationStage implements ElevationGenerator {
    public static final GenerationStageId STAGE_ID = GenerationStageId.of("world:elevation");
    public static final long SEA_LEVEL_SUBUNITS = 0L;

    private static final GenerationPurposeId COARSE = GenerationPurposeId.of("world:coarse");
    private static final GenerationPurposeId MEDIUM = GenerationPurposeId.of("world:medium");
    private static final GenerationPurposeId DETAIL = GenerationPurposeId.of("world:detail");
    private static final GenerationPurposeId LANDMASS = GenerationPurposeId.of("world:landmass");
    private static final GenerationPurposeId FRAGMENT = GenerationPurposeId.of("world:fragment");
    private static final GenerationPurposeId UPLIFT = GenerationPurposeId.of("world:uplift");
    private static final GenerationPurposeId RIDGE_A = GenerationPurposeId.of("world:ridge-a");
    private static final GenerationPurposeId RIDGE_B = GenerationPurposeId.of("world:ridge-b");
    private static final GenerationPurposeId BASIN = GenerationPurposeId.of("world:basin");
    private static final GenerationPurposeId WARP_X = GenerationPurposeId.of("world:morphology-warp-x");
    private static final GenerationPurposeId WARP_Y = GenerationPurposeId.of("world:morphology-warp-y");
    private static final int SAMPLE_MAX = 65_535;

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        GenerationRevision revision = genesis.generationRevision();
        if (GenerationRevision.V11.equals(revision)) return generateOrganicMorphology(genesis);
        if (GenerationRevision.V10.equals(revision)) return generateMacroMorphology(genesis);
        if (GenerationRevision.V9.equals(revision)) return generateOceanFirst(genesis);
        if (!GenerationRevision.V1.equals(revision)
                && !GenerationRevision.V2.equals(revision)
                && !GenerationRevision.V3.equals(revision)
                && !GenerationRevision.V4.equals(revision)
                && !GenerationRevision.V5.equals(revision)
                && !GenerationRevision.V6.equals(revision)
                && !GenerationRevision.V7.equals(revision)
                && !GenerationRevision.V8.equals(revision)) {
            throw new IllegalArgumentException("unsupported generation revision: " + revision.value());
        }
        return generateLegacy(genesis, !GenerationRevision.V1.equals(revision));
    }

    private static ElevationField generateLegacy(WorldGenesis genesis, boolean precise) {
        WorldBounds bounds = genesis.spec().bounds();
        long[] elevations = new long[DenseElevationField.cellCount(bounds)];
        GenerationRandom random = GenerationRandom.from(genesis);
        long width = (long) bounds.maxX() - bounds.minX() + 1L;
        long height = (long) bounds.maxY() - bounds.minY() + 1L;
        int index = 0;
        for (long localY = 0; localY < height; localY++) {
            int y = (int) ((long) bounds.minY() + localY);
            for (long localX = 0; localX < width; localX++) {
                int x = (int) ((long) bounds.minX() + localX);
                elevations[index++] = legacyElevationSubunitsAt(random, bounds, x, y, precise);
            }
        }
        return new DenseElevationField(bounds, elevations);
    }

    /** V9 is intentionally isolated so the first ocean-first revision remains reproducible. */
    private static ElevationField generateOceanFirst(WorldGenesis genesis) {
        WorldBounds bounds = genesis.spec().bounds();
        validateOceanFirstBounds(bounds);
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = DenseElevationField.cellCount(bounds);
        WorldGenerationIntent intent = genesis.generationIntent();
        GenerationRandom random = GenerationRandom.from(genesis);

        int maxDimension = Math.max(width, height);
        int coherentScale = interpolatedScale(4, Math.max(4, maxDimension), intent.landmassScale());
        int fragmentedScale = Math.max(2, coherentScale / 4);
        int fragmentPpm = intent.fragmentation().partsPerMillion();
        long[] rankKeys = new long[area];

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int coherent = valueNoise(random, LANDMASS, x, y, coherentScale);
                int fragmented = valueNoise(random, FRAGMENT, x, y, fragmentedScale);
                int potential = (int) (((long) coherent * (NormalizedValue.SCALE - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / NormalizedValue.SCALE);
                rankKeys[index] = rankKey(potential, index);
                index++;
            }
        }
        Arrays.sort(rankKeys);

        int landCount = calibratedLandCount(area, intent.landCoverage());
        long[] elevations = new long[area];
        long landAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long oceanAmplitude = Math.multiplyExact(
                -(long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        for (int rank = 0; rank < area; rank++) {
            int cell = (int) rankKeys[rank];
            if (rank < landCount) {
                elevations[cell] = positiveRankHeight(rank, landCount, landAmplitude);
            } else {
                elevations[cell] = -positiveRankHeight(
                        area - 1 - rank, area - landCount, oceanAmplitude);
            }
        }
        return new DenseElevationField(bounds, elevations);
    }

    /**
     * V10 preserves the calibrated V9 land/ocean mask and adds continent-scale relief inside land.
     * This method stays isolated because its exact output is a stable revision contract.
     */
    private static ElevationField generateMacroMorphology(WorldGenesis genesis) {
        return generateStructuredMorphology(genesis, false);
    }

    /**
     * V11 keeps the same authored intent but removes the piecewise-linear signature of V10.
     * Smooth interpolation and low-frequency domain warping produce rounded coastlines, basins and
     * relief belts while exact coverage calibration still chooses the requested number of land cells.
     */
    private static ElevationField generateOrganicMorphology(WorldGenesis genesis) {
        return generateStructuredMorphology(genesis, true);
    }

    private static ElevationField generateStructuredMorphology(WorldGenesis genesis, boolean organic) {
        WorldBounds bounds = genesis.spec().bounds();
        validateOceanFirstBounds(bounds);
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = DenseElevationField.cellCount(bounds);
        WorldGenerationIntent intent = genesis.generationIntent();
        GenerationRandom random = GenerationRandom.from(genesis);

        int maxDimension = Math.max(width, height);
        int coherentScale = interpolatedScale(4, Math.max(4, maxDimension), intent.landmassScale());
        int fragmentedScale = Math.max(2, coherentScale / 4);
        int fragmentPpm = intent.fragmentation().partsPerMillion();
        long[] rankKeys = new long[area];

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int coherent = morphologyNoise(random, LANDMASS, x, y, coherentScale, organic);
                int fragmented = morphologyNoise(random, FRAGMENT, x, y, fragmentedScale, organic);
                int potential = (int) (((long) coherent * (NormalizedValue.SCALE - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / NormalizedValue.SCALE);
                rankKeys[index] = rankKey(potential, index);
                index++;
            }
        }
        Arrays.sort(rankKeys);

        int landCount = calibratedLandCount(area, intent.landCoverage());
        long[] elevations = new long[area];
        long landAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long oceanAmplitude = Math.multiplyExact(
                -(long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        int upliftScale = Math.max(8, maxDimension / 3);
        int ridgeScale = Math.max(4, maxDimension / 10);
        int basinScale = Math.max(8, maxDimension / 4);
        int reliefPpm = intent.relief().partsPerMillion();

        for (int rank = 0; rank < area; rank++) {
            int cell = (int) rankKeys[rank];
            if (rank >= landCount) {
                elevations[cell] = -positiveRankHeight(
                        area - 1 - rank, area - landCount, oceanAmplitude);
                continue;
            }

            int localY = cell / width;
            int localX = cell - localY * width;
            int x = bounds.minX() + localX;
            int y = bounds.minY() + localY;
            int interiorityPpm = landCount <= 1
                    ? NormalizedValue.SCALE
                    : (int) (((long) (landCount - 1 - rank) * NormalizedValue.SCALE)
                            / (landCount - 1L));

            int upliftPpm = sampleToPpm(morphologyNoise(random, UPLIFT, x, y, upliftScale, organic));
            int ridgePpm = ridgeStrengthPpm(random, x, y, ridgeScale, organic);
            int basinPpm = sampleToPpm(morphologyNoise(random, BASIN, x, y, basinScale, organic));

            long macroPpm = 250_000L
                    + ((long) (upliftPpm - NormalizedValue.SCALE / 2) * 45L) / 100L
                    + ((long) ridgePpm * 55L) / 100L
                    - ((long) basinPpm * 30L) / 100L;
            int structuredPpm = clampPpm(macroPpm);

            int coastalGatePpm = 250_000 + (int) (((long) interiorityPpm * 750_000L)
                    / NormalizedValue.SCALE);
            structuredPpm = (int) (((long) structuredPpm * coastalGatePpm)
                    / NormalizedValue.SCALE);

            int lowlandPpm = 60_000 + (int) (((long) interiorityPpm * 120_000L)
                    / NormalizedValue.SCALE);
            int heightPpm = lowlandPpm + (int) (((long) (structuredPpm - lowlandPpm)
                    * reliefPpm) / NormalizedValue.SCALE);
            elevations[cell] = positiveNormalizedHeight(clampPpm(heightPpm), landAmplitude);
        }
        return new DenseElevationField(bounds, elevations);
    }

    private static void validateOceanFirstBounds(WorldBounds bounds) {
        if (bounds.minZ() >= 0 || bounds.maxZ() <= 0) {
            throw new IllegalArgumentException(
                    "ocean-first generation requires world bounds below and above sea level z=0");
        }
    }

    /** Sort ascending by descending potential, then ascending stable cell index. */
    private static long rankKey(int potential, int cellIndex) {
        long invertedPotential = SAMPLE_MAX - potential;
        return (invertedPotential << 32) | (cellIndex & 0xffff_ffffL);
    }

    private static int calibratedLandCount(int area, NormalizedValue coverage) {
        return Math.toIntExact(((long) area * coverage.partsPerMillion() + NormalizedValue.SCALE / 2L)
                / NormalizedValue.SCALE);
    }

    private static long positiveRankHeight(int rankFromExtreme, int count, long amplitude) {
        if (count <= 0) return 0L;
        if (count == 1) return Math.max(1L, amplitude);
        long remaining = (long) count - rankFromExtreme - 1L;
        return 1L + ((amplitude - 1L) * remaining) / (count - 1L);
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / NormalizedValue.SCALE;
    }

    private static int ridgeStrengthPpm(
            GenerationRandom random, int x, int y, int scale, boolean organic) {
        int first = morphologyNoise(random, RIDGE_A, x, y, scale, organic);
        int second = morphologyNoise(random, RIDGE_B, x, y, scale, organic);
        long differencePpm = (long) Math.abs(first - second) * NormalizedValue.SCALE / SAMPLE_MAX;
        return clampPpm(NormalizedValue.SCALE - differencePpm * 2L);
    }

    private static int morphologyNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            int x,
            int y,
            int scale,
            boolean organic) {
        return organic
                ? organicValueNoise(random, purpose, x, y, scale)
                : valueNoise(random, purpose, x, y, scale);
    }

    private static int organicValueNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            int x,
            int y,
            int scale) {
        int warpScale = Math.max(8, scale * 2);
        int warpAmplitude = Math.max(1, scale / 5);
        int warpXSample = smoothValueNoise(random, WARP_X, x, y, warpScale);
        int warpYSample = smoothValueNoise(random, WARP_Y, x, y, warpScale);
        int warpedX = x + centeredSampleOffset(warpXSample, warpAmplitude);
        int warpedY = y + centeredSampleOffset(warpYSample, warpAmplitude);
        return smoothValueNoise(random, purpose, warpedX, warpedY, scale);
    }

    private static int centeredSampleOffset(int sample, int amplitude) {
        long centered = (long) sample * 2L - SAMPLE_MAX;
        return (int) ((centered * amplitude) / SAMPLE_MAX);
    }

    private static int sampleToPpm(int sample) {
        return (int) (((long) sample * NormalizedValue.SCALE) / SAMPLE_MAX);
    }

    private static int clampPpm(long value) {
        return (int) Math.max(0L, Math.min((long) NormalizedValue.SCALE, value));
    }

    private static int interpolatedScale(int min, int max, NormalizedValue coordinate) {
        return min + (int) (((long) (max - min) * coordinate.partsPerMillion())
                / NormalizedValue.SCALE);
    }

    private static long legacyElevationSubunitsAt(
            GenerationRandom random, WorldBounds bounds, int x, int y, boolean precise) {
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
                discreteElevation, ElevationField.SUBUNITS_PER_CELL);
        if (!precise) return discreteSubunits;
        long remainder = numerator % SAMPLE_MAX;
        long fractionalSubunits = (remainder * ElevationField.SUBUNITS_PER_CELL) / SAMPLE_MAX;
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

    private static int smoothValueNoise(
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
        int lower = smoothInterpolate(lowerLeft, lowerRight, offsetX, scale);
        int upper = smoothInterpolate(upperLeft, upperRight, offsetX, scale);
        return smoothInterpolate(lower, upper, offsetY, scale);
    }

    private static int sample(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long latticeX,
            long latticeY) {
        return (int) ((random.sampleLong(
                STAGE_ID, purpose, latticeX, latticeY, 0L, 0L) >>> 48) & SAMPLE_MAX);
    }

    private static int interpolate(int from, int to, int offset, int scale) {
        return (int) (((long) from * (scale - offset) + (long) to * offset) / scale);
    }

    private static int smoothInterpolate(int from, int to, int offset, int scale) {
        long coordinate = ((long) offset * NormalizedValue.SCALE) / scale;
        long coordinateSquared = coordinate * coordinate;
        long fade = coordinateSquared
                * (3L * NormalizedValue.SCALE - 2L * coordinate)
                / ((long) NormalizedValue.SCALE * NormalizedValue.SCALE);
        return (int) (((long) from * (NormalizedValue.SCALE - fade) + (long) to * fade)
                / NormalizedValue.SCALE);
    }
}
