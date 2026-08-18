package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * V12 terrain authoring built around explicit multi-scale landforms rather than a stack of
 * unconstrained noise bands.
 *
 * <p>The generator deliberately separates four spatial roles:
 * continent/coast membership, broad uplift, balanced hill/depression kernels, and rolling local
 * relief. A narrow ridged field is mixed in only through the authored ruggedness coordinate. All
 * relief wavelengths are expressed in terrain-cell space, so a larger world contains more
 * landforms instead of stretching one feature into a huge plateau. A final deterministic slope
 * relaxation keeps the precise height field readable when projected onto discrete terrain cells.</p>
 */
final class V12LandformElevationGenerator {
    private static final GenerationPurposeId LANDMASS = GenerationPurposeId.of("world:landmass");
    private static final GenerationPurposeId FRAGMENT = GenerationPurposeId.of("world:fragment");
    private static final GenerationPurposeId UPLIFT = GenerationPurposeId.of("world:v12-uplift");
    private static final GenerationPurposeId RIDGE_A = GenerationPurposeId.of("world:v12-ridge-a");
    private static final GenerationPurposeId RIDGE_B = GenerationPurposeId.of("world:v12-ridge-b");
    private static final GenerationPurposeId ROLLING = GenerationPurposeId.of("world:v12-rolling");
    private static final GenerationPurposeId ROLLING_DETAIL =
            GenerationPurposeId.of("world:v12-rolling-detail");
    private static final GenerationPurposeId LANDFORM_FEATURE =
            GenerationPurposeId.of("world:v12-landform-feature");
    private static final GenerationPurposeId LANDFORM_PATTERN =
            GenerationPurposeId.of("world:v12-landform-pattern");
    private static final GenerationPurposeId WARP_X = GenerationPurposeId.of("world:v12-warp-x");
    private static final GenerationPurposeId WARP_Y = GenerationPurposeId.of("world:v12-warp-y");

    private static final int SAMPLE_MAX = 65_535;
    private static final int PPM = NormalizedValue.SCALE;

    private static final int MIN_LANDFORM_SPACING = 20;
    private static final int MAX_LANDFORM_SPACING = 64;
    private static final int COASTAL_TRANSITION_CELLS = 12;

    private static final int COAST_BASE_HEIGHT_PPM = 70_000;
    private static final int INTERIOR_BASE_HEIGHT_PPM = 230_000;
    private static final int COAST_RELIEF_GATE_PPM = 250_000;

    private static final int UPLIFT_WEIGHT_PPM = 220_000;
    private static final int LANDFORM_WEIGHT_PPM = 340_000;
    private static final int RIDGE_WEIGHT_PPM = 300_000;
    private static final int ROLLING_WEIGHT_PPM = 180_000;
    private static final int ROLLING_PRIMARY_WEIGHT_PPM = 760_000;
    private static final int ROLLING_DETAIL_WEIGHT_PPM = PPM - ROLLING_PRIMARY_WEIGHT_PPM;
    private static final int NEGATIVE_RELIEF_COMPRESSION_PPM = 650_000;

    private static final int FEATURE_JITTER_PPM = 260_000;
    private static final int FEATURE_RADIUS_MIN_PPM = 650_000;
    private static final int FEATURE_RADIUS_RANGE_PPM = 270_000;
    private static final int FEATURE_MAGNITUDE_MIN_PPM = 550_000;
    private static final int FEATURE_MAGNITUDE_RANGE_PPM = 450_000;

    private static final int MIN_SLOPE_PPM = 180_000;
    private static final int MAX_SLOPE_PPM = 600_000;
    private static final int SLOPE_RELAXATION_PASSES = 4;

    private V12LandformElevationGenerator() {
    }

    static ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        WorldBounds bounds = genesis.spec().bounds();
        validateBounds(bounds);

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = DenseElevationField.cellCount(bounds);
        WorldGenerationIntent intent = genesis.generationIntent();
        GenerationRandom random = GenerationRandom.from(genesis);

        long[] rankKeys = calibratedLandRankKeys(random, bounds, width, height, intent);
        int landCount = calibratedLandCount(area, intent.landCoverage());
        boolean[] land = new boolean[area];
        for (int rank = 0; rank < landCount; rank++) {
            land[(int) rankKeys[rank]] = true;
        }
        int[] coastalInteriority = coastalInteriorityPpm(land, width, height);

        long landAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long oceanAmplitude = Math.multiplyExact(
                -(long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        long[] elevations = new long[area];

        int landformSpacing = landformSpacing(intent.landformScale());
        int upliftScale = Math.max(52, landformSpacing * 2);
        int ridgeScale = Math.max(34, landformSpacing * 3 / 2);
        int rollingScale = Math.max(16, landformSpacing / 2);
        int rollingDetailScale = Math.max(10, landformSpacing / 3);
        int reliefPpm = intent.relief().partsPerMillion();
        int localReliefPpm = intent.localRelief().partsPerMillion();
        int ruggednessPpm = intent.ruggedness().partsPerMillion();

        for (int rank = 0; rank < area; rank++) {
            int cell = (int) rankKeys[rank];
            if (!land[cell]) {
                elevations[cell] = -positiveRankHeight(
                        area - 1 - rank,
                        area - landCount,
                        oceanAmplitude);
                continue;
            }

            int localY = cell / width;
            int localX = cell - localY * width;
            int x = bounds.minX() + localX;
            int y = bounds.minY() + localY;
            int interiorityPpm = coastalInteriority[cell];

            long upliftPpm = centeredPpm(organicValueNoise(
                    random,
                    UPLIFT,
                    x,
                    y,
                    upliftScale));
            long landformPpm = landformFieldPpm(
                    random,
                    x,
                    y,
                    landformSpacing);
            int ridgePpm = ridgeCrestPpm(random, x, y, ridgeScale);
            long rollingPpm = rollingFieldPpm(
                    random,
                    x,
                    y,
                    rollingScale,
                    rollingDetailScale);

            long macroSignalPpm = weightedCentered(upliftPpm, UPLIFT_WEIGHT_PPM)
                    + weightedCentered(landformPpm, LANDFORM_WEIGHT_PPM)
                    + (long) ridgePpm * RIDGE_WEIGHT_PPM * ruggednessPpm
                            / PPM / PPM;
            macroSignalPpm = macroSignalPpm * reliefPpm / PPM;

            long localSignalPpm = rollingPpm * ROLLING_WEIGHT_PPM / PPM;
            localSignalPpm = localSignalPpm * localReliefPpm / PPM;

            long reliefSignalPpm = macroSignalPpm + localSignalPpm;
            if (reliefSignalPpm < 0L) {
                reliefSignalPpm = reliefSignalPpm * NEGATIVE_RELIEF_COMPRESSION_PPM / PPM;
            }

            int coastGatePpm = COAST_RELIEF_GATE_PPM
                    + (int) ((long) interiorityPpm * (PPM - COAST_RELIEF_GATE_PPM) / PPM);
            reliefSignalPpm = reliefSignalPpm * coastGatePpm / PPM;

            long baseHeightPpm = COAST_BASE_HEIGHT_PPM
                    + (long) interiorityPpm * INTERIOR_BASE_HEIGHT_PPM / PPM;
            int heightPpm = clampPpm(baseHeightPpm + reliefSignalPpm);
            elevations[cell] = positiveNormalizedHeight(heightPpm, landAmplitude);
        }

        long maximumStep = maximumReadableStepSubunits(ruggednessPpm);
        relaxLandSlopes(
                elevations,
                land,
                width,
                height,
                maximumStep,
                landAmplitude);
        return new DenseElevationField(bounds, elevations);
    }

    /**
     * The coast mask remains calibrated exactly by rank. Only terrain elevation inside that mask is
     * replaced, keeping land coverage and land/ocean membership deterministic and independent from
     * terrain-character controls.
     */
    private static long[] calibratedLandRankKeys(
            GenerationRandom random,
            WorldBounds bounds,
            int width,
            int height,
            WorldGenerationIntent intent) {
        int maxDimension = Math.max(width, height);
        int coherentScale = interpolatedScale(4, Math.max(4, maxDimension), intent.landmassScale());
        int fragmentedScale = Math.max(2, coherentScale / 4);
        int fragmentPpm = intent.fragmentation().partsPerMillion();
        long[] rankKeys = new long[Math.multiplyExact(width, height)];

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int coherent = organicValueNoise(random, LANDMASS, x, y, coherentScale);
                int fragmented = organicValueNoise(random, FRAGMENT, x, y, fragmentedScale);
                int potential = (int) (((long) coherent * (PPM - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / PPM);
                rankKeys[index] = rankKey(potential, index);
                index++;
            }
        }
        Arrays.sort(rankKeys);
        return rankKeys;
    }

    /**
     * One deterministic feature lives in every feature-lattice cell. Each 2x2 group contains two
     * hills and two depressions, but the pattern phase, center, radius and amplitude are randomized.
     * This prevents an unlucky small map from becoming all peaks or all bowls while avoiding a
     * visible regular checkerboard.
     */
    private static long landformFieldPpm(
            GenerationRandom random,
            int x,
            int y,
            int spacing) {
        long latticeX = Math.floorDiv((long) x, spacing);
        long latticeY = Math.floorDiv((long) y, spacing);
        long xPpm = (long) x * PPM;
        long yPpm = (long) y * PPM;
        long sum = 0L;

        for (int offsetY = -1; offsetY <= 1; offsetY++) {
            long featureY = latticeY + offsetY;
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                long featureX = latticeX + offsetX;

                int jitterX = centeredRandomPpm(random, LANDFORM_FEATURE, featureX, featureY, 0L);
                int jitterY = centeredRandomPpm(random, LANDFORM_FEATURE, featureX, featureY, 1L);
                long centerX = featureX * spacing * (long) PPM
                        + (long) spacing * PPM / 2L
                        + (long) jitterX * spacing * FEATURE_JITTER_PPM / PPM;
                long centerY = featureY * spacing * (long) PPM
                        + (long) spacing * PPM / 2L
                        + (long) jitterY * spacing * FEATURE_JITTER_PPM / PPM;

                int radiusCoordinate = randomPpm(
                        random,
                        LANDFORM_FEATURE,
                        featureX,
                        featureY,
                        2L);
                int radiusFactorPpm = FEATURE_RADIUS_MIN_PPM
                        + (int) ((long) radiusCoordinate * FEATURE_RADIUS_RANGE_PPM / PPM);
                long radius = (long) spacing * radiusFactorPpm;

                long dx = xPpm - centerX;
                long dy = yPpm - centerY;
                long normalizedX = dx * PPM / radius;
                long normalizedY = dy * PPM / radius;
                long distanceSquaredPpm = (normalizedX * normalizedX
                        + normalizedY * normalizedY) / PPM;
                if (distanceSquaredPpm >= PPM) continue;

                int falloffPpm = smoothStepPpm(PPM - distanceSquaredPpm);
                int magnitudeCoordinate = randomPpm(
                        random,
                        LANDFORM_FEATURE,
                        featureX,
                        featureY,
                        3L);
                int magnitudePpm = FEATURE_MAGNITUDE_MIN_PPM
                        + (int) ((long) magnitudeCoordinate * FEATURE_MAGNITUDE_RANGE_PPM / PPM);
                int sign = landformSign(random, featureX, featureY);
                sum += (long) sign * magnitudePpm * falloffPpm / PPM;
            }
        }
        return clampCenteredPpm(sum);
    }

    private static int landformSign(
            GenerationRandom random,
            long featureX,
            long featureY) {
        long blockX = Math.floorDiv(featureX, 2L);
        long blockY = Math.floorDiv(featureY, 2L);
        int phase = randomPpm(random, LANDFORM_PATTERN, blockX, blockY, 0L) >= PPM / 2 ? 1 : 0;
        return ((featureX + featureY + phase) & 1L) == 0L ? 1 : -1;
    }

    private static int ridgeCrestPpm(
            GenerationRandom random,
            int x,
            int y,
            int scale) {
        int first = organicValueNoise(random, RIDGE_A, x, y, scale);
        int second = organicValueNoise(random, RIDGE_B, x, y, scale);
        long differencePpm = (long) Math.abs(first - second) * PPM / SAMPLE_MAX;
        int rawRidgePpm = clampPpm(PPM - differencePpm * 2L);
        if (rawRidgePpm <= 500_000) return 0;
        long crestCoordinate = Math.min((long) PPM, (rawRidgePpm - 500_000L) * 2L);
        int smooth = smoothStepPpm(crestCoordinate);
        return (int) ((long) smooth * smooth / PPM);
    }

    private static long rollingFieldPpm(
            GenerationRandom random,
            int x,
            int y,
            int primaryScale,
            int detailScale) {
        long primary = centeredPpm(smoothValueNoise(random, ROLLING, x, y, primaryScale));
        long detail = centeredPpm(smoothValueNoise(random, ROLLING_DETAIL, x, y, detailScale));
        return (primary * ROLLING_PRIMARY_WEIGHT_PPM
                + detail * ROLLING_DETAIL_WEIGHT_PPM) / PPM;
    }

    /**
     * A bounded pairwise relaxation removes isolated cell-scale cliffs without erasing broad
     * landforms. Ruggedness widens the allowed precise cardinal step from 0.18 to 0.60 terrain
     * cells, so rugged worlds can keep strong slopes while calm worlds remain visually legible.
     */
    private static void relaxLandSlopes(
            long[] elevations,
            boolean[] land,
            int width,
            int height,
            long maximumStep,
            long maximumHeight) {
        for (int pass = 0; pass < SLOPE_RELAXATION_PASSES; pass++) {
            boolean reverse = (pass & 1) != 0;
            if (!reverse) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x + 1 < width) {
                            relaxPair(elevations, land, cell, cell + 1, maximumStep, maximumHeight);
                        }
                        if (y + 1 < height) {
                            relaxPair(elevations, land, cell, cell + width, maximumStep, maximumHeight);
                        }
                    }
                }
            } else {
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = width - 1; x >= 0; x--) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x > 0) {
                            relaxPair(elevations, land, cell, cell - 1, maximumStep, maximumHeight);
                        }
                        if (y > 0) {
                            relaxPair(elevations, land, cell, cell - width, maximumStep, maximumHeight);
                        }
                    }
                }
            }
        }
    }

    private static void relaxPair(
            long[] elevations,
            boolean[] land,
            int first,
            int second,
            long maximumStep,
            long maximumHeight) {
        if (!land[first] || !land[second]) return;
        long difference = elevations[first] - elevations[second];
        long magnitude = Math.abs(difference);
        if (magnitude <= maximumStep) return;

        long excess = magnitude - maximumStep;
        long firstCorrection = (excess + 1L) / 2L;
        long secondCorrection = excess - firstCorrection;
        if (difference > 0L) {
            elevations[first] = clampLandHeight(elevations[first] - firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] + secondCorrection, maximumHeight);
        } else {
            elevations[first] = clampLandHeight(elevations[first] + firstCorrection, maximumHeight);
            elevations[second] = clampLandHeight(elevations[second] - secondCorrection, maximumHeight);
        }
    }

    private static long clampLandHeight(long value, long maximumHeight) {
        return Math.max(1L, Math.min(maximumHeight, value));
    }

    private static long maximumReadableStepSubunits(int ruggednessPpm) {
        int slopePpm = MIN_SLOPE_PPM
                + (int) ((long) ruggednessPpm * (MAX_SLOPE_PPM - MIN_SLOPE_PPM) / PPM);
        return Math.max(
                1L,
                (long) ElevationField.SUBUNITS_PER_CELL * slopePpm / PPM);
    }

    private static int landformSpacing(NormalizedValue scale) {
        return MIN_LANDFORM_SPACING
                + (int) ((long) (MAX_LANDFORM_SPACING - MIN_LANDFORM_SPACING)
                        * scale.partsPerMillion() / PPM);
    }

    private static long weightedCentered(long centeredPpm, int weightPpm) {
        return centeredPpm * weightPpm / PPM;
    }

    private static long centeredPpm(int sample) {
        return (long) sampleToPpm(sample) * 2L - PPM;
    }

    private static long clampCenteredPpm(long value) {
        return Math.max(-(long) PPM, Math.min((long) PPM, value));
    }

    private static int randomPpm(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        int sample = (int) ((random.sampleLong(
                ElevationGenerationStage.STAGE_ID,
                purpose,
                x,
                y,
                0L,
                ordinal) >>> 48) & SAMPLE_MAX);
        return sampleToPpm(sample);
    }

    private static int centeredRandomPpm(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, purpose, x, y, ordinal) * 2 - PPM;
    }

    private static int organicValueNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            int x,
            int y,
            int scale) {
        int warpScale = Math.max(8, scale * 2);
        int warpAmplitude = Math.max(1, scale / 6);
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
                ElevationGenerationStage.STAGE_ID,
                purpose,
                latticeX,
                latticeY,
                0L,
                0L) >>> 48) & SAMPLE_MAX);
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

    private static int sampleToPpm(int sample) {
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    private static int clampPpm(long value) {
        return (int) Math.max(0L, Math.min((long) PPM, value));
    }

    private static int interpolatedScale(int min, int max, NormalizedValue coordinate) {
        return min + (int) ((long) (max - min) * coordinate.partsPerMillion() / PPM);
    }

    private static long rankKey(int potential, int cellIndex) {
        long invertedPotential = SAMPLE_MAX - potential;
        return (invertedPotential << 32) | (cellIndex & 0xffff_ffffL);
    }

    private static int calibratedLandCount(int area, NormalizedValue coverage) {
        return Math.toIntExact(((long) area * coverage.partsPerMillion() + PPM / 2L) / PPM);
    }

    private static long positiveRankHeight(int rankFromExtreme, int count, long amplitude) {
        if (count <= 0) return 0L;
        if (count == 1) return Math.max(1L, amplitude);
        long remaining = (long) count - rankFromExtreme - 1L;
        return 1L + ((amplitude - 1L) * remaining) / (count - 1L);
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / PPM;
    }

    private static int[] coastalInteriorityPpm(boolean[] land, int width, int height) {
        int[] distance = new int[land.length];
        int infinity = width + height + 1;
        boolean hasOcean = false;
        for (int index = 0; index < land.length; index++) {
            if (land[index]) {
                distance[index] = infinity;
            } else {
                distance[index] = 0;
                hasOcean = true;
            }
        }

        int[] result = new int[land.length];
        if (!hasOcean) {
            Arrays.fill(result, PPM);
            return result;
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (!land[index]) continue;
                int best = distance[index];
                if (x > 0) best = Math.min(best, distance[index - 1] + 1);
                if (y > 0) best = Math.min(best, distance[index - width] + 1);
                distance[index] = best;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int index = y * width + x;
                if (!land[index]) continue;
                int best = distance[index];
                if (x + 1 < width) best = Math.min(best, distance[index + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[index + width] + 1);
                distance[index] = best;
            }
        }

        for (int index = 0; index < land.length; index++) {
            if (!land[index]) continue;
            long coordinate = Math.min(distance[index], COASTAL_TRANSITION_CELLS)
                    * (long) PPM / COASTAL_TRANSITION_CELLS;
            result[index] = smoothStepPpm(coordinate);
        }
        return result;
    }

    private static void validateBounds(WorldBounds bounds) {
        if (bounds.minZ() >= 0 || bounds.maxZ() <= 0) {
            throw new IllegalArgumentException(
                    "ocean-first generation requires world bounds below and above sea level z=0");
        }
    }
}
