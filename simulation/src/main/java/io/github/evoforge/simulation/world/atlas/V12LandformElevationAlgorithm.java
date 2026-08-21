package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * Pure deterministic spatial synthesis for the accepted V12 base terrain.
 *
 * <p>All world-specific operating values arrive through {@link V12LandformCalibration}; all V12
 * model choices arrive through {@link V12LandformRecipe}. Later compositions may supply a typed
 * landmass silhouette before rank selection without changing accepted V12 relief behavior.</p>
 */
final class V12LandformElevationAlgorithm {
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
    private static final int POTENTIAL_BUCKETS = SAMPLE_MAX + 2;
    private static final int PPM = NormalizedValue.SCALE;

    ElevationField generate(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        return generate(
                genesis,
                calibration,
                recipe,
                LandmassSilhouette.unconstrained(genesis.spec().bounds()));
    }

    ElevationField generate(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassSilhouette silhouette) {
        PreparedLandRanking ranking = prepareLandRanking(genesis, calibration, recipe, silhouette);
        return generate(genesis, calibration, recipe, silhouette, ranking);
    }

    PreparedLandRanking prepareLandRanking(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassSilhouette silhouette) {
        if (genesis == null || calibration == null || recipe == null || silhouette == null) {
            throw new IllegalArgumentException("V12 generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!bounds.equals(silhouette.bounds())) {
            throw new IllegalArgumentException("landmass silhouette must match generation bounds");
        }
        V12RandomStreams random = V12RandomStreams.bind(GenerationRandom.from(genesis));
        return calibratedLandRanking(random, bounds, calibration, recipe, silhouette);
    }

    ElevationField generate(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassSilhouette silhouette,
            PreparedLandRanking ranking) {
        if (genesis == null
                || calibration == null
                || recipe == null
                || silhouette == null
                || ranking == null) {
            throw new IllegalArgumentException("V12 generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!bounds.equals(silhouette.bounds())) {
            throw new IllegalArgumentException("landmass silhouette must match generation bounds");
        }
        int width = calibration.width();
        int height = calibration.height();
        int area = calibration.area();
        if (ranking.potentialSamples().length != area
                || ranking.startRankByBucket().length != POTENTIAL_BUCKETS) {
            throw new IllegalArgumentException("prepared V12 land ranking must match generation area");
        }
        V12RandomStreams random = V12RandomStreams.bind(GenerationRandom.from(genesis));

        int landCount = Math.min(calibration.landCount(), silhouette.supportCellCount());
        boolean[] land = selectedLand(ranking, silhouette, landCount);
        int[] coastalInteriority = coastalInteriorityPpm(
                land,
                width,
                height,
                recipe.coast().transitionCells());

        long landAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long oceanAmplitude = Math.multiplyExact(
                -(long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        long[] elevations = new long[area];

        LandformFeatureGrid landforms = LandformFeatureGrid.create(
                random,
                bounds,
                calibration.landformSpacing(),
                recipe);
        V12LandformRecipe.ReliefMix relief = recipe.relief();
        V12LandformRecipe.CoastProfile coast = recipe.coast();

        int[] seenByBucket = new int[POTENTIAL_BUCKETS];
        for (int cell = 0; cell < area; cell++) {
            int bucket = rankingBucket(ranking, silhouette, cell);
            int rank = ranking.startRankByBucket()[bucket] + seenByBucket[bucket]++;
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
                    random.uplift(),
                    x,
                    y,
                    calibration.upliftScale(),
                    recipe));
            long landformPpm = landformFieldPpm(landforms, x, y, recipe.features());
            int ridgePpm = ridgeCrestPpm(
                    random,
                    x,
                    y,
                    calibration.ridgeScale(),
                    recipe);
            long rollingPpm = rollingFieldPpm(
                    random,
                    x,
                    y,
                    calibration.rollingScale(),
                    calibration.rollingDetailScale(),
                    recipe);

            long macroSignalPpm = weightedCentered(upliftPpm, relief.upliftWeightPpm())
                    + weightedCentered(landformPpm, relief.landformWeightPpm())
                    + (long) ridgePpm * relief.ridgeWeightPpm() * calibration.ruggednessPpm()
                            / PPM / PPM;
            macroSignalPpm = macroSignalPpm * calibration.reliefPpm() / PPM;

            long localSignalPpm = rollingPpm * relief.rollingWeightPpm() / PPM;
            localSignalPpm = localSignalPpm * calibration.localReliefPpm() / PPM;

            long reliefSignalPpm = macroSignalPpm + localSignalPpm;
            if (reliefSignalPpm < 0L) {
                reliefSignalPpm = reliefSignalPpm * relief.negativeReliefCompressionPpm() / PPM;
            }

            int coastGatePpm = coast.minimumReliefGatePpm()
                    + (int) ((long) interiorityPpm * (PPM - coast.minimumReliefGatePpm()) / PPM);
            reliefSignalPpm = reliefSignalPpm * coastGatePpm / PPM;

            long baseHeightPpm = coast.baseHeightPpm()
                    + (long) interiorityPpm * coast.interiorHeightPpm() / PPM;
            int heightPpm = clampPpm(baseHeightPpm + reliefSignalPpm);
            elevations[cell] = positiveNormalizedHeight(heightPpm, landAmplitude);
        }

        relaxLandSlopes(
                elevations,
                land,
                width,
                height,
                calibration.maximumReadableStepSubunits(),
                landAmplitude,
                recipe.slopes().relaxationPasses());
        return new DenseElevationField(bounds, elevations);
    }

    /**
     * Computes the exact old comparison-sort rank from a bounded 16-bit potential domain.
     *
     * <p>Bucket zero represents unsupported potential -1. Supported potential {@code p} uses bucket
     * {@code p + 1}. Start ranks are accumulated from highest potential to lowest. A later linear
     * scan increments {@code seen[bucket]}, reproducing the old cell-index tie-break exactly without
     * allocating or sorting one 64-bit key per cell.</p>
     */
    private static PreparedLandRanking calibratedLandRanking(
            V12RandomStreams random,
            WorldBounds bounds,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassSilhouette silhouette) {
        int width = calibration.width();
        int height = calibration.height();
        int fragmentPpm = calibration.fragmentationPpm();
        char[] potentialSamples = new char[calibration.area()];
        int[] bucketCounts = new int[POTENTIAL_BUCKETS];

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                if (!silhouette.supportsIndex(index)) {
                    bucketCounts[0]++;
                    index++;
                    continue;
                }

                int x = bounds.minX() + localX;
                int coherent = organicValueNoise(
                        random,
                        random.landmass(),
                        x,
                        y,
                        calibration.coherentLandmassScale(),
                        recipe);
                int fragmented = organicValueNoise(
                        random,
                        random.fragment(),
                        x,
                        y,
                        calibration.fragmentedLandmassScale(),
                        recipe);
                int potential = (int) (((long) coherent * (PPM - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / PPM);
                if (silhouette.constrained()) {
                    int basePpm = sampleToPpm(potential);
                    int influencePpm = silhouette.influencePpm();
                    int blendedPpm = Math.toIntExact(
                            ((long) basePpm * (PPM - influencePpm)
                                    + (long) silhouette.potentialPpmAtIndex(index) * influencePpm) / PPM);
                    potential = ppmToSample(blendedPpm);
                }
                potentialSamples[index] = (char) potential;
                bucketCounts[potential + 1]++;
                index++;
            }
        }

        int[] startRankByBucket = new int[POTENTIAL_BUCKETS];
        int runningRank = 0;
        for (int bucket = POTENTIAL_BUCKETS - 1; bucket >= 0; bucket--) {
            startRankByBucket[bucket] = runningRank;
            runningRank = Math.addExact(runningRank, bucketCounts[bucket]);
        }
        if (runningRank != calibration.area()) {
            throw new IllegalStateException("V12 potential histogram did not cover generation area");
        }
        return new PreparedLandRanking(potentialSamples, startRankByBucket);
    }

    private static boolean[] selectedLand(
            PreparedLandRanking ranking,
            LandmassSilhouette silhouette,
            int landCount) {
        boolean[] land = new boolean[ranking.potentialSamples().length];
        int[] seenByBucket = new int[POTENTIAL_BUCKETS];
        for (int cell = 0; cell < land.length; cell++) {
            int bucket = rankingBucket(ranking, silhouette, cell);
            int rank = ranking.startRankByBucket()[bucket] + seenByBucket[bucket]++;
            if (rank < landCount) land[cell] = true;
        }
        return land;
    }

    private static int rankingBucket(
            PreparedLandRanking ranking,
            LandmassSilhouette silhouette,
            int cell) {
        return silhouette.supportsIndex(cell)
                ? ranking.potentialSamples()[cell] + 1
                : 0;
    }

    private static long landformFieldPpm(
            LandformFeatureGrid grid,
            int x,
            int y,
            V12LandformRecipe.FeatureKernel policy) {
        long latticeX = Math.floorDiv((long) x, grid.spacing());
        long latticeY = Math.floorDiv((long) y, grid.spacing());
        long xPpm = (long) x * PPM;
        long yPpm = (long) y * PPM;
        long sum = 0L;
        int neighborhood = policy.neighborhoodRadius();

        for (int offsetY = -neighborhood; offsetY <= neighborhood; offsetY++) {
            long featureY = latticeY + offsetY;
            for (int offsetX = -neighborhood; offsetX <= neighborhood; offsetX++) {
                long featureX = latticeX + offsetX;
                LandformFeature feature = grid.get(featureX, featureY);

                long dx = xPpm - feature.centerXPpm();
                long dy = yPpm - feature.centerYPpm();
                long normalizedX = dx * PPM / feature.radiusPpm();
                long normalizedY = dy * PPM / feature.radiusPpm();
                long distanceSquaredPpm = (normalizedX * normalizedX
                        + normalizedY * normalizedY) / PPM;
                if (distanceSquaredPpm >= PPM) continue;

                int falloffPpm = smoothStepPpm(PPM - distanceSquaredPpm);
                sum += (long) feature.signedMagnitudePpm() * falloffPpm / PPM;
            }
        }
        return clampCenteredPpm(sum);
    }

    private static LandformFeature createLandformFeature(
            V12RandomStreams random,
            long featureX,
            long featureY,
            int spacing,
            V12LandformRecipe recipe) {
        V12LandformRecipe.FeatureKernel policy = recipe.features();
        int jitterX = centeredRandomPpm(random.landformFeature(), featureX, featureY, 0L);
        int jitterY = centeredRandomPpm(random.landformFeature(), featureX, featureY, 1L);
        long centerX = featureX * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterX * spacing * policy.jitterPpm() / PPM;
        long centerY = featureY * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterY * spacing * policy.jitterPpm() / PPM;

        int radiusCoordinate = randomPpm(random.landformFeature(), featureX, featureY, 2L);
        int radiusFactorPpm = policy.minimumRadiusPpm()
                + (int) ((long) radiusCoordinate * policy.radiusRangePpm() / PPM);
        long radius = (long) spacing * radiusFactorPpm;

        int magnitudeCoordinate = randomPpm(random.landformFeature(), featureX, featureY, 3L);
        int magnitudePpm = policy.minimumMagnitudePpm()
                + (int) ((long) magnitudeCoordinate * policy.magnitudeRangePpm() / PPM);
        int sign = landformSign(random.landformPattern(), featureX, featureY, policy.balanceBlockSize());
        return new LandformFeature(centerX, centerY, radius, sign * magnitudePpm);
    }

    private static int landformSign(
            GenerationRandom.BoundSampler random,
            long featureX,
            long featureY,
            int balanceBlockSize) {
        long blockX = Math.floorDiv(featureX, balanceBlockSize);
        long blockY = Math.floorDiv(featureY, balanceBlockSize);
        int phase = randomPpm(random, blockX, blockY, 0L) >= PPM / 2 ? 1 : 0;
        return ((featureX + featureY + phase) & 1L) == 0L ? 1 : -1;
    }

    private static int ridgeCrestPpm(
            V12RandomStreams random,
            int x,
            int y,
            int scale,
            V12LandformRecipe recipe) {
        int first = organicValueNoise(random, random.ridgeA(), x, y, scale, recipe);
        int second = organicValueNoise(random, random.ridgeB(), x, y, scale, recipe);
        long differencePpm = (long) Math.abs(first - second) * PPM / SAMPLE_MAX;
        int rawRidgePpm = clampPpm(PPM - differencePpm * 2L);
        int threshold = recipe.noise().ridgeCrestThresholdPpm();
        if (rawRidgePpm <= threshold) return 0;
        long crestCoordinate = Math.min(
                (long) PPM,
                (rawRidgePpm - (long) threshold) * PPM / (PPM - (long) threshold));
        int smooth = smoothStepPpm(crestCoordinate);
        return (int) ((long) smooth * smooth / PPM);
    }

    private static long rollingFieldPpm(
            V12RandomStreams random,
            int x,
            int y,
            int primaryScale,
            int detailScale,
            V12LandformRecipe recipe) {
        long primary = centeredPpm(smoothValueNoise(random.rolling(), x, y, primaryScale));
        long detail = centeredPpm(smoothValueNoise(random.rollingDetail(), x, y, detailScale));
        V12LandformRecipe.ReliefMix mix = recipe.relief();
        return (primary * mix.rollingPrimaryWeightPpm()
                + detail * mix.rollingDetailWeightPpm()) / PPM;
    }

    private static void relaxLandSlopes(
            long[] elevations,
            boolean[] land,
            int width,
            int height,
            long maximumStep,
            long maximumHeight,
            int passes) {
        for (int pass = 0; pass < passes; pass++) {
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
            GenerationRandom.BoundSampler random,
            long x,
            long y,
            long ordinal) {
        int sample = (int) ((random.sampleLong(x, y, 0L, ordinal) >>> 48) & SAMPLE_MAX);
        return sampleToPpm(sample);
    }

    private static int centeredRandomPpm(
            GenerationRandom.BoundSampler random,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, x, y, ordinal) * 2 - PPM;
    }

    private static int organicValueNoise(
            V12RandomStreams random,
            GenerationRandom.BoundSampler purpose,
            int x,
            int y,
            int scale,
            V12LandformRecipe recipe) {
        V12LandformRecipe.NoisePolicy noise = recipe.noise();
        int warpScale = Math.max(noise.minimumWarpScale(), scale * noise.warpScaleMultiplier());
        int warpAmplitude = Math.max(1, scale / noise.warpAmplitudeDivisor());
        int warpXSample = smoothValueNoise(random.warpX(), x, y, warpScale);
        int warpYSample = smoothValueNoise(random.warpY(), x, y, warpScale);
        int warpedX = x + centeredSampleOffset(warpXSample, warpAmplitude);
        int warpedY = y + centeredSampleOffset(warpYSample, warpAmplitude);
        return smoothValueNoise(purpose, warpedX, warpedY, scale);
    }

    private static int centeredSampleOffset(int sample, int amplitude) {
        long centered = (long) sample * 2L - SAMPLE_MAX;
        return (int) ((centered * amplitude) / SAMPLE_MAX);
    }

    private static int smoothValueNoise(
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

    private static int sampleToPpm(int sample) {
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    private static int ppmToSample(int ppm) {
        return (int) ((long) clampPpm(ppm) * SAMPLE_MAX / PPM);
    }

    private static int clampPpm(long value) {
        return (int) Math.max(0L, Math.min((long) PPM, value));
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

    private static int[] coastalInteriorityPpm(
            boolean[] land,
            int width,
            int height,
            int transitionCells) {
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

        if (!hasOcean) {
            Arrays.fill(distance, PPM);
            return distance;
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
            long coordinate = Math.min(distance[index], transitionCells)
                    * (long) PPM / transitionCells;
            distance[index] = smoothStepPpm(coordinate);
        }
        return distance;
    }

    record PreparedLandRanking(
            char[] potentialSamples,
            int[] startRankByBucket) {
        PreparedLandRanking {
            if (potentialSamples == null || startRankByBucket == null) {
                throw new IllegalArgumentException("prepared V12 ranking buffers must not be null");
            }
        }
    }

    /** Bound V12 semantic random streams reused by every hot sample in one world. */
    private record V12RandomStreams(
            GenerationRandom.BoundSampler landmass,
            GenerationRandom.BoundSampler fragment,
            GenerationRandom.BoundSampler uplift,
            GenerationRandom.BoundSampler ridgeA,
            GenerationRandom.BoundSampler ridgeB,
            GenerationRandom.BoundSampler rolling,
            GenerationRandom.BoundSampler rollingDetail,
            GenerationRandom.BoundSampler landformFeature,
            GenerationRandom.BoundSampler landformPattern,
            GenerationRandom.BoundSampler warpX,
            GenerationRandom.BoundSampler warpY) {

        static V12RandomStreams bind(GenerationRandom random) {
            return new V12RandomStreams(
                    random.bind(ElevationGenerationStage.STAGE_ID, LANDMASS),
                    random.bind(ElevationGenerationStage.STAGE_ID, FRAGMENT),
                    random.bind(ElevationGenerationStage.STAGE_ID, UPLIFT),
                    random.bind(ElevationGenerationStage.STAGE_ID, RIDGE_A),
                    random.bind(ElevationGenerationStage.STAGE_ID, RIDGE_B),
                    random.bind(ElevationGenerationStage.STAGE_ID, ROLLING),
                    random.bind(ElevationGenerationStage.STAGE_ID, ROLLING_DETAIL),
                    random.bind(ElevationGenerationStage.STAGE_ID, LANDFORM_FEATURE),
                    random.bind(ElevationGenerationStage.STAGE_ID, LANDFORM_PATTERN),
                    random.bind(ElevationGenerationStage.STAGE_ID, WARP_X),
                    random.bind(ElevationGenerationStage.STAGE_ID, WARP_Y));
        }
    }

    private record LandformFeature(
            long centerXPpm,
            long centerYPpm,
            long radiusPpm,
            int signedMagnitudePpm) {
    }

    private static final class LandformFeatureGrid {
        private final long minFeatureX;
        private final long minFeatureY;
        private final int width;
        private final int height;
        private final int spacing;
        private final LandformFeature[] features;

        private LandformFeatureGrid(
                long minFeatureX,
                long minFeatureY,
                int width,
                int height,
                int spacing,
                LandformFeature[] features) {
            this.minFeatureX = minFeatureX;
            this.minFeatureY = minFeatureY;
            this.width = width;
            this.height = height;
            this.spacing = spacing;
            this.features = features;
        }

        static LandformFeatureGrid create(
                V12RandomStreams random,
                WorldBounds bounds,
                int spacing,
                V12LandformRecipe recipe) {
            int neighborhood = recipe.features().neighborhoodRadius();
            long minFeatureX = Math.floorDiv((long) bounds.minX(), spacing) - neighborhood;
            long maxFeatureX = Math.floorDiv((long) bounds.maxX(), spacing) + neighborhood;
            long minFeatureY = Math.floorDiv((long) bounds.minY(), spacing) - neighborhood;
            long maxFeatureY = Math.floorDiv((long) bounds.maxY(), spacing) + neighborhood;
            int width = Math.toIntExact(maxFeatureX - minFeatureX + 1L);
            int height = Math.toIntExact(maxFeatureY - minFeatureY + 1L);
            LandformFeature[] features = new LandformFeature[Math.multiplyExact(width, height)];

            int index = 0;
            for (long featureY = minFeatureY; featureY <= maxFeatureY; featureY++) {
                for (long featureX = minFeatureX; featureX <= maxFeatureX; featureX++) {
                    features[index++] = createLandformFeature(
                            random,
                            featureX,
                            featureY,
                            spacing,
                            recipe);
                }
            }
            return new LandformFeatureGrid(
                    minFeatureX,
                    minFeatureY,
                    width,
                    height,
                    spacing,
                    features);
        }

        int spacing() {
            return spacing;
        }

        LandformFeature get(long featureX, long featureY) {
            long localX = featureX - minFeatureX;
            long localY = featureY - minFeatureY;
            if (localX < 0L || localX >= width || localY < 0L || localY >= height) {
                throw new IllegalArgumentException(
                        "landform feature outside cached grid: " + featureX + "," + featureY);
            }
            return features[Math.toIntExact(localY * width + localX)];
        }
    }
}
