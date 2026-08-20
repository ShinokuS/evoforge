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
 * model choices arrive through {@link V12LandformRecipe}. Later compositions may additionally
 * constrain the landmass domain before rank selection without changing accepted V12 behavior.</p>
 */
final class V12LandformElevationAlgorithm {
    private static final GenerationPurposeId LANDMASS = GenerationPurposeId.of("world:landmass");
    private static final GenerationPurposeId FRAGMENT = GenerationPurposeId.of("world:fragment");
    private static final GenerationPurposeId OCEAN_DOMAIN_MACRO =
            GenerationPurposeId.of("world:v14-ocean-domain-macro");
    private static final GenerationPurposeId OCEAN_DOMAIN_DETAIL =
            GenerationPurposeId.of("world:v14-ocean-domain-detail");
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

    ElevationField generate(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe) {
        return generate(
                genesis,
                calibration,
                recipe,
                LandmassBoundaryCalibration.unconstrained(calibration.area()));
    }

    ElevationField generate(
            WorldGenesis genesis,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassBoundaryCalibration boundary) {
        if (genesis == null || calibration == null || recipe == null || boundary == null) {
            throw new IllegalArgumentException("V12 generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        int width = calibration.width();
        int height = calibration.height();
        int area = calibration.area();
        GenerationRandom random = GenerationRandom.from(genesis);

        LandmassDomain domain = createLandmassDomain(
                random,
                bounds,
                calibration,
                recipe,
                boundary);
        long[] rankKeys = calibratedLandRankKeys(
                random,
                bounds,
                calibration,
                recipe,
                boundary,
                domain);
        int landCount = Math.min(calibration.landCount(), domain.supportCellCount());
        boolean[] land = new boolean[area];
        for (int rank = 0; rank < landCount; rank++) {
            land[(int) rankKeys[rank]] = true;
        }
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
     * Resolves the maximum V14 terrestrial support before authored land coverage is applied.
     *
     * <p>This is deliberately a rank selection over a broad continuous field. At maximum authored
     * land, the generated silhouette is therefore this organic support itself rather than every cell
     * inside a rectangular edge margin. The hard margin remains only a safety invariant: it can
     * never become the coastline merely because the requested land amount is high.</p>
     */
    private static LandmassDomain createLandmassDomain(
            GenerationRandom random,
            WorldBounds bounds,
            V12LandformCalibration calibration,
            V12LandformRecipe terrainRecipe,
            LandmassBoundaryCalibration boundary) {
        int area = calibration.area();
        if (!boundary.oceanBounded()) {
            boolean[] support = new boolean[area];
            Arrays.fill(support, true);
            return new LandmassDomain(support, null, area);
        }

        int width = calibration.width();
        int height = calibration.height();
        int[] domainPotentialPpm = new int[area];
        long[] domainRanks = new long[area];
        int transition = Math.max(1, boundary.detailScaleCells());

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int edgeDistance = edgeDistance(localX, localY, width, height);
                if (edgeDistance < boundary.minimumOceanMarginCells()) {
                    domainPotentialPpm[index] = 0;
                    domainRanks[index] = rankKey(-1, index);
                    index++;
                    continue;
                }

                int rawDomainPpm = organicDomainPotentialPpm(
                        random,
                        x,
                        y,
                        localX,
                        localY,
                        width,
                        height,
                        boundary,
                        terrainRecipe);
                long coastCoordinate = (long) (edgeDistance
                        - boundary.minimumOceanMarginCells() + 1) * PPM / transition;
                int oceanGatePpm = smoothStepPpm(coastCoordinate);
                int supportPpm = Math.toIntExact((long) rawDomainPpm * oceanGatePpm / PPM);
                domainPotentialPpm[index] = supportPpm;
                domainRanks[index] = rankKey(ppmToSample(supportPpm), index);
                index++;
            }
        }

        Arrays.sort(domainRanks);
        int supportCellCount = Math.min(boundary.maximumLandCells(), area);
        boolean[] support = new boolean[area];
        for (int rank = 0; rank < supportCellCount; rank++) {
            int cell = (int) domainRanks[rank];
            if (domainPotentialPpm[cell] <= 0) {
                supportCellCount = rank;
                break;
            }
            support[cell] = true;
        }
        return new LandmassDomain(support, domainPotentialPpm, supportCellCount);
    }

    private static long[] calibratedLandRankKeys(
            GenerationRandom random,
            WorldBounds bounds,
            V12LandformCalibration calibration,
            V12LandformRecipe recipe,
            LandmassBoundaryCalibration boundary,
            LandmassDomain domain) {
        int width = calibration.width();
        int height = calibration.height();
        int fragmentPpm = calibration.fragmentationPpm();
        long[] rankKeys = new long[calibration.area()];

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int coherent = organicValueNoise(
                        random,
                        LANDMASS,
                        x,
                        y,
                        calibration.coherentLandmassScale(),
                        recipe);
                int fragmented = organicValueNoise(
                        random,
                        FRAGMENT,
                        x,
                        y,
                        calibration.fragmentedLandmassScale(),
                        recipe);
                int potential = (int) (((long) coherent * (PPM - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / PPM);
                if (!domain.support()[index]) {
                    potential = -1;
                } else if (boundary.oceanBounded()) {
                    int domainPpm = domain.potentialPpm()[index];
                    int influencePpm = boundary.domainInfluencePpm();
                    int basePpm = sampleToPpm(potential);
                    int blendedPpm = Math.toIntExact(
                            ((long) basePpm * (PPM - influencePpm)
                                    + (long) domainPpm * influencePpm) / PPM);
                    potential = ppmToSample(blendedPpm);
                }
                rankKeys[index] = rankKey(potential, index);
                index++;
            }
        }
        Arrays.sort(rankKeys);
        return rankKeys;
    }

    private static int organicDomainPotentialPpm(
            GenerationRandom random,
            int x,
            int y,
            int localX,
            int localY,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            V12LandformRecipe terrainRecipe) {
        int centerPpm = continentalCenterPotentialPpm(localX, localY, width, height);
        int macroPpm = sampleToPpm(organicValueNoise(
                random,
                OCEAN_DOMAIN_MACRO,
                x,
                y,
                boundary.macroScaleCells(),
                terrainRecipe));
        int detailPpm = sampleToPpm(organicValueNoise(
                random,
                OCEAN_DOMAIN_DETAIL,
                x,
                y,
                boundary.detailScaleCells(),
                terrainRecipe));
        return Math.toIntExact(
                ((long) centerPpm * boundary.centerWeightPpm()
                        + (long) macroPpm * boundary.macroWeightPpm()
                        + (long) detailPpm * boundary.detailWeightPpm()) / PPM);
    }

    private static int edgeDistance(
            int localX,
            int localY,
            int width,
            int height) {
        return Math.min(
                Math.min(localX, width - 1 - localX),
                Math.min(localY, height - 1 - localY));
    }

    private static int continentalCenterPotentialPpm(
            int localX,
            int localY,
            int width,
            int height) {
        long xDenominator = Math.max(1L, width - 1L);
        long yDenominator = Math.max(1L, height - 1L);
        long centeredX = Math.abs(2L * localX - (width - 1L));
        long centeredY = Math.abs(2L * localY - (height - 1L));
        long xPpm = centeredX * PPM / xDenominator;
        long yPpm = centeredY * PPM / yDenominator;
        long radialSquaredPpm = (xPpm * xPpm + yPpm * yPpm) / PPM;
        int radialPpm = clampPpm(radialSquaredPpm);
        return PPM - smoothStepPpm(radialPpm);
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
            GenerationRandom random,
            long featureX,
            long featureY,
            int spacing,
            V12LandformRecipe recipe) {
        V12LandformRecipe.FeatureKernel policy = recipe.features();
        int jitterX = centeredRandomPpm(random, LANDFORM_FEATURE, featureX, featureY, 0L);
        int jitterY = centeredRandomPpm(random, LANDFORM_FEATURE, featureX, featureY, 1L);
        long centerX = featureX * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterX * spacing * policy.jitterPpm() / PPM;
        long centerY = featureY * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterY * spacing * policy.jitterPpm() / PPM;

        int radiusCoordinate = randomPpm(random, LANDFORM_FEATURE, featureX, featureY, 2L);
        int radiusFactorPpm = policy.minimumRadiusPpm()
                + (int) ((long) radiusCoordinate * policy.radiusRangePpm() / PPM);
        long radius = (long) spacing * radiusFactorPpm;

        int magnitudeCoordinate = randomPpm(random, LANDFORM_FEATURE, featureX, featureY, 3L);
        int magnitudePpm = policy.minimumMagnitudePpm()
                + (int) ((long) magnitudeCoordinate * policy.magnitudeRangePpm() / PPM);
        int sign = landformSign(random, featureX, featureY, policy.balanceBlockSize());
        return new LandformFeature(centerX, centerY, radius, sign * magnitudePpm);
    }

    private static int landformSign(
            GenerationRandom random,
            long featureX,
            long featureY,
            int balanceBlockSize) {
        long blockX = Math.floorDiv(featureX, balanceBlockSize);
        long blockY = Math.floorDiv(featureY, balanceBlockSize);
        int phase = randomPpm(random, LANDFORM_PATTERN, blockX, blockY, 0L) >= PPM / 2 ? 1 : 0;
        return ((featureX + featureY + phase) & 1L) == 0L ? 1 : -1;
    }

    private static int ridgeCrestPpm(
            GenerationRandom random,
            int x,
            int y,
            int scale,
            V12LandformRecipe recipe) {
        int first = organicValueNoise(random, RIDGE_A, x, y, scale, recipe);
        int second = organicValueNoise(random, RIDGE_B, x, y, scale, recipe);
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
            GenerationRandom random,
            int x,
            int y,
            int primaryScale,
            int detailScale,
            V12LandformRecipe recipe) {
        long primary = centeredPpm(smoothValueNoise(random, ROLLING, x, y, primaryScale));
        long detail = centeredPpm(smoothValueNoise(random, ROLLING_DETAIL, x, y, detailScale));
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
            int scale,
            V12LandformRecipe recipe) {
        V12LandformRecipe.NoisePolicy noise = recipe.noise();
        int warpScale = Math.max(noise.minimumWarpScale(), scale * noise.warpScaleMultiplier());
        int warpAmplitude = Math.max(1, scale / noise.warpAmplitudeDivisor());
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

    private static int ppmToSample(int ppm) {
        return (int) ((long) clampPpm(ppm) * SAMPLE_MAX / PPM);
    }

    private static int clampPpm(long value) {
        return (int) Math.max(0L, Math.min((long) PPM, value));
    }

    private static long rankKey(int potential, int cellIndex) {
        long invertedPotential = (long) SAMPLE_MAX - potential;
        return (invertedPotential << 32) | (cellIndex & 0xffff_ffffL);
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
            long coordinate = Math.min(distance[index], transitionCells)
                    * (long) PPM / transitionCells;
            result[index] = smoothStepPpm(coordinate);
        }
        return result;
    }

    private record LandmassDomain(
            boolean[] support,
            int[] potentialPpm,
            int supportCellCount) {
        private LandmassDomain {
            if (support == null || supportCellCount < 0 || supportCellCount > support.length) {
                throw new IllegalArgumentException("landmass domain support must be valid");
            }
            if (potentialPpm != null && potentialPpm.length != support.length) {
                throw new IllegalArgumentException("landmass domain potential must match support domain");
            }
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
                GenerationRandom random,
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
