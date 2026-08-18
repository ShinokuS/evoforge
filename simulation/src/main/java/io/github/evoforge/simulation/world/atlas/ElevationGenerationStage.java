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

/**
 * Deterministic elevation generation; V9 adds oceans, V10 macro relief, V11 organic morphology,
 * and V12 makes terrain relief spatially stable across different world dimensions.
 */
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
    private static final GenerationPurposeId LOCAL_RELIEF = GenerationPurposeId.of("world:local-relief");
    private static final GenerationPurposeId LOCAL_RELIEF_DETAIL =
            GenerationPurposeId.of("world:local-relief-detail");
    private static final GenerationPurposeId WARP_X = GenerationPurposeId.of("world:morphology-warp-x");
    private static final GenerationPurposeId WARP_Y = GenerationPurposeId.of("world:morphology-warp-y");
    private static final int SAMPLE_MAX = 65_535;

    // V12 relief wavelengths are expressed in terrain-cell space. Increasing the map dimensions
    // therefore reveals more terrain features instead of stretching the same feature into a plateau.
    private static final int V12_UPLIFT_SCALE = 112;
    private static final int V12_RIDGE_SCALE = 56;
    private static final int V12_BASIN_SCALE = 144;
    private static final int V12_LOCAL_RELIEF_PRIMARY_SCALE = 32;
    private static final int V12_LOCAL_RELIEF_DETAIL_SCALE = 14;
    private static final int V12_LOCAL_RELIEF_PRIMARY_WEIGHT_PPM = 700_000;
    private static final int V12_LOCAL_RELIEF_DETAIL_WEIGHT_PPM =
            NormalizedValue.SCALE - V12_LOCAL_RELIEF_PRIMARY_WEIGHT_PPM;
    private static final int V12_COASTAL_TRANSITION_CELLS = 12;
    private static final long V12_LOCAL_RELIEF_MAX_AMPLITUDE_SUBUNITS =
            7L * ElevationField.SUBUNITS_PER_CELL;
    private static final long V12_LOCAL_RELIEF_QUIET_SLOPE_SUBUNITS =
            ElevationField.SUBUNITS_PER_CELL / 8L;
    private static final long V12_LOCAL_RELIEF_BUSY_SLOPE_SUBUNITS =
            ElevationField.SUBUNITS_PER_CELL * 3L / 4L;

    @Override
    public ElevationField generate(WorldGenesis genesis) {
        if (genesis == null) throw new IllegalArgumentException("genesis must not be null");
        GenerationRevision revision = genesis.generationRevision();
        if (GenerationRevision.V12.equals(revision)) return generateScaleStableMorphology(genesis);
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

    /**
     * Stable V10/V11 morphology implementation. V12 is intentionally separate so fixing terrain
     * scale does not alter any already-published revision output.
     */
    private static ElevationField generateStructuredMorphology(
            WorldGenesis genesis,
            boolean organic) {
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

    /**
     * V12 keeps V11's calibrated land mask, but terrain height no longer depends on global land
     * rank or world-size-scaled relief wavelengths. Coastal influence comes from actual spatial
     * distance to ocean, and relief wavelengths remain stable in terrain-cell space.
     */
    private static ElevationField generateScaleStableMorphology(WorldGenesis genesis) {
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
                int coherent = morphologyNoise(random, LANDMASS, x, y, coherentScale, true);
                int fragmented = morphologyNoise(random, FRAGMENT, x, y, fragmentedScale, true);
                int potential = (int) (((long) coherent * (NormalizedValue.SCALE - fragmentPpm)
                        + (long) fragmented * fragmentPpm) / NormalizedValue.SCALE);
                rankKeys[index] = rankKey(potential, index);
                index++;
            }
        }
        Arrays.sort(rankKeys);

        int landCount = calibratedLandCount(area, intent.landCoverage());
        boolean[] land = new boolean[area];
        for (int rank = 0; rank < landCount; rank++) land[(int) rankKeys[rank]] = true;
        int[] coastalInteriority = coastalInteriorityPpm(land, width, height);

        long[] elevations = new long[area];
        long landAmplitude = Math.multiplyExact(
                (long) bounds.maxZ(), ElevationField.SUBUNITS_PER_CELL);
        long oceanAmplitude = Math.multiplyExact(
                -(long) bounds.minZ(), ElevationField.SUBUNITS_PER_CELL);
        int reliefPpm = intent.relief().partsPerMillion();
        int localReliefPpm = intent.localRelief().partsPerMillion();

        // First build the macro surface only. Local relief is applied in a second pass so its
        // strength can respond to the actual local macro slope instead of blindly adding noise to
        // already-busy compact terrain.
        for (int rank = 0; rank < area; rank++) {
            int cell = (int) rankKeys[rank];
            if (!land[cell]) {
                elevations[cell] = -positiveRankHeight(
                        area - 1 - rank, area - landCount, oceanAmplitude);
                continue;
            }

            int localY = cell / width;
            int localX = cell - localY * width;
            int x = bounds.minX() + localX;
            int y = bounds.minY() + localY;
            int interiorityPpm = coastalInteriority[cell];

            int upliftPpm = sampleToPpm(
                    organicValueNoise(random, UPLIFT, x, y, V12_UPLIFT_SCALE));
            int ridgePpm = ridgeStrengthPpm(random, x, y, V12_RIDGE_SCALE, true);
            int basinPpm = sampleToPpm(
                    organicValueNoise(random, BASIN, x, y, V12_BASIN_SCALE));

            long macroPpm = 250_000L
                    + ((long) (upliftPpm - NormalizedValue.SCALE / 2) * 45L) / 100L
                    + ((long) ridgePpm * 55L) / 100L
                    - ((long) basinPpm * 30L) / 100L;
            int structuredPpm = clampPpm(macroPpm);

            int coastalGatePpm = 300_000 + (int) (((long) interiorityPpm * 700_000L)
                    / NormalizedValue.SCALE);
            structuredPpm = (int) (((long) structuredPpm * coastalGatePpm)
                    / NormalizedValue.SCALE);

            int lowlandPpm = 70_000 + (int) (((long) interiorityPpm * 110_000L)
                    / NormalizedValue.SCALE);
            int heightPpm = lowlandPpm + (int) (((long) (structuredPpm - lowlandPpm)
                    * reliefPpm) / NormalizedValue.SCALE);
            elevations[cell] = positiveNormalizedHeight(clampPpm(heightPpm), landAmplitude);
        }

        if (localReliefPpm == 0) {
            return new DenseElevationField(bounds, elevations);
        }

        long[] macroElevations = elevations;
        long[] locallyVaried = Arrays.copyOf(macroElevations, macroElevations.length);
        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                int cell = localY * width + localX;
                if (!land[cell]) continue;

                int calmnessPpm = localReliefCalmnessPpm(
                        macroElevations,
                        land,
                        width,
                        height,
                        localX,
                        localY);
                if (calmnessPpm == 0) continue;

                int x = bounds.minX() + localX;
                int y = bounds.minY() + localY;
                locallyVaried[cell] = addV12LocalRelief(
                        macroElevations[cell],
                        landAmplitude,
                        random,
                        x,
                        y,
                        localReliefPpm,
                        calmnessPpm);
            }
        }
        return new DenseElevationField(bounds, locallyVaried);
    }

    /**
     * Local relief is strongest on genuinely broad macro shelves and fades out on already-steep
     * terrain. This makes the authored control solve the original problem directly: large plateaus
     * gain rolling hills, while a small high-relief world does not become noisy everywhere.
     */
    private static int localReliefCalmnessPpm(
            long[] macroElevations,
            boolean[] land,
            int width,
            int height,
            int x,
            int y) {
        int cell = y * width + x;
        long center = macroElevations[cell];
        long maximumStep = 0L;
        int neighbours = 0;

        if (x > 0 && land[cell - 1]) {
            maximumStep = Math.max(maximumStep, absoluteDifference(center, macroElevations[cell - 1]));
            neighbours++;
        }
        if (x + 1 < width && land[cell + 1]) {
            maximumStep = Math.max(maximumStep, absoluteDifference(center, macroElevations[cell + 1]));
            neighbours++;
        }
        if (y > 0 && land[cell - width]) {
            maximumStep = Math.max(maximumStep, absoluteDifference(center, macroElevations[cell - width]));
            neighbours++;
        }
        if (y + 1 < height && land[cell + width]) {
            maximumStep = Math.max(maximumStep, absoluteDifference(center, macroElevations[cell + width]));
            neighbours++;
        }
        if (neighbours == 0) return 0;
        if (maximumStep <= V12_LOCAL_RELIEF_QUIET_SLOPE_SUBUNITS) {
            return NormalizedValue.SCALE;
        }
        if (maximumStep >= V12_LOCAL_RELIEF_BUSY_SLOPE_SUBUNITS) return 0;

        long coordinate = (maximumStep - V12_LOCAL_RELIEF_QUIET_SLOPE_SUBUNITS)
                * NormalizedValue.SCALE
                / (V12_LOCAL_RELIEF_BUSY_SLOPE_SUBUNITS
                        - V12_LOCAL_RELIEF_QUIET_SLOPE_SUBUNITS);
        return NormalizedValue.SCALE - smoothStepPpm(coordinate);
    }

    private static long addV12LocalRelief(
            long baseElevation,
            long landAmplitude,
            GenerationRandom random,
            int x,
            int y,
            int localReliefPpm,
            int calmnessPpm) {
        // No domain warp here: local relief must stay spatially calm and predictable. The smaller
        // secondary band breaks very long quantized shelves without introducing cell-scale noise.
        long primaryPpm = centeredPpm(smoothValueNoise(
                random,
                LOCAL_RELIEF,
                x,
                y,
                V12_LOCAL_RELIEF_PRIMARY_SCALE));
        long detailPpm = centeredPpm(smoothValueNoise(
                random,
                LOCAL_RELIEF_DETAIL,
                x,
                y,
                V12_LOCAL_RELIEF_DETAIL_SCALE));
        long combinedPpm = (primaryPpm * V12_LOCAL_RELIEF_PRIMARY_WEIGHT_PPM
                + detailPpm * V12_LOCAL_RELIEF_DETAIL_WEIGHT_PPM)
                / NormalizedValue.SCALE;
        long shapedPpm = boostLocalReliefSignal(combinedPpm);

        long fullStrengthOffset = shapedPpm * V12_LOCAL_RELIEF_MAX_AMPLITUDE_SUBUNITS
                / NormalizedValue.SCALE;
        long authoredOffset = fullStrengthOffset * localReliefPpm / NormalizedValue.SCALE;
        long offset = authoredOffset * calmnessPpm / NormalizedValue.SCALE;
        return Math.max(1L, Math.min(landAmplitude, baseElevation + offset));
    }

    /** Boosts mid-strength hills/valleys while preserving zero and the authored maximum. */
    private static long boostLocalReliefSignal(long centeredPpm) {
        long magnitude = Math.min((long) NormalizedValue.SCALE, Math.abs(centeredPpm));
        long multiplierPpm = 1_300_000L - magnitude * 300_000L / NormalizedValue.SCALE;
        long boosted = centeredPpm * multiplierPpm / NormalizedValue.SCALE;
        return Math.max(-(long) NormalizedValue.SCALE,
                Math.min((long) NormalizedValue.SCALE, boosted));
    }

    private static long centeredPpm(int sample) {
        return (long) sampleToPpm(sample) * 2L - NormalizedValue.SCALE;
    }

    private static long absoluteDifference(long first, long second) {
        long difference = first - second;
        if (difference == Long.MIN_VALUE) {
            throw new ArithmeticException("elevation difference exceeds signed range");
        }
        return Math.abs(difference);
    }

    /**
     * Spatial coast distance replaces V10/V11's global land-potential rank as a terrain-height
     * input. That removes the small-world checkerboard effect and makes the same terrain settings
     * have comparable local character on 64x64 and much larger worlds.
     */
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
            Arrays.fill(result, NormalizedValue.SCALE);
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
            long coordinate = Math.min(distance[index], V12_COASTAL_TRANSITION_CELLS)
                    * (long) NormalizedValue.SCALE / V12_COASTAL_TRANSITION_CELLS;
            result[index] = smoothStepPpm(coordinate);
        }
        return result;
    }

    private static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) NormalizedValue.SCALE, coordinatePpm));
        long coordinateSquared = coordinate * coordinate;
        return (int) (coordinateSquared
                * (3L * NormalizedValue.SCALE - 2L * coordinate)
                / ((long) NormalizedValue.SCALE * NormalizedValue.SCALE));
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
