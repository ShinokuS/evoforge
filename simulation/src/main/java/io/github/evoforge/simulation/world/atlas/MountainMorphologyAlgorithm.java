package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic spatial synthesis for V13 mountain structure.
 *
 * <p>The accepted V12 hills remain the visual reference. Each mountain starts as one very large
 * anisotropic smooth hill, with a subordinate smooth inner core for vertical presence. Once that
 * macro form is composed with V12, the actual final surface inside the mountain footprint is
 * relaxed against an abstract cardinal-rise budget. The mountain stage never queries or names a
 * concrete runtime Shape.</p>
 */
final class MountainMorphologyAlgorithm {
    private static final GenerationStageId STAGE_ID = GenerationStageId.of("world:mountains");
    private static final GenerationPurposeId ACTIVE = GenerationPurposeId.of("mountain:active");
    private static final GenerationPurposeId CENTER = GenerationPurposeId.of("mountain:center");
    private static final GenerationPurposeId ORIENTATION = GenerationPurposeId.of("mountain:orientation");
    private static final GenerationPurposeId WIDTH = GenerationPurposeId.of("mountain:width");
    private static final GenerationPurposeId HEIGHT = GenerationPurposeId.of("mountain:height");
    private static final GenerationPurposeId PLATEAU = GenerationPurposeId.of("mountain:plateau");
    private static final int PPM = NormalizedValue.SCALE;
    private static final double TWO_PI = StrictMath.PI * 2.0;
    private static final int MAX_FINAL_SURFACE_RELAXATION_PASSES = 24;

    ElevationField generate(
            WorldGenesis genesis,
            ElevationField base,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        if (genesis == null || base == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("mountain generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!sameHorizontalBounds(bounds, base.bounds())) {
            throw new IllegalArgumentException("mountain base elevation must cover the same horizontal world");
        }

        int width = calibration.width();
        int height = calibration.height();
        long[] baseHeights = new long[calibration.area()];
        boolean[] land = new boolean[calibration.area()];
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = base.elevationSubunitsAt(x, y);
                baseHeights[index] = value;
                land[index] = value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                index++;
            }
        }

        if (calibration.candidateActivationPpm() == 0 || calibration.typicalUpliftSubunits() == 0L) {
            return new DenseElevationField(bounds, baseHeights);
        }

        long[] mountainUplift = new long[calibration.area()];
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(random, bounds, calibration, recipe)) {
            rasterize(system, bounds, width, height, land, mountainUplift, calibration, recipe);
        }

        smoothUplift(
                mountainUplift,
                land,
                width,
                height,
                recipe.upliftSmoothingPasses());

        long maximumRawUplift = maximum(mountainUplift);
        if (maximumRawUplift <= 0L) {
            return new DenseElevationField(bounds, baseHeights);
        }

        int requiredCoastalDistance = requiredCoastalDistance(
                maximumRawUplift,
                calibration.shorelineUpliftSubunits(),
                calibration.maximumCardinalRiseSubunits(),
                calibration.coastalTransitionCells());
        int[] coastalDistance = distanceFromOceanCells(
                land,
                width,
                height,
                requiredCoastalDistance);
        long[] upliftCaps = coastalUpliftCaps(
                land,
                coastalDistance,
                maximumRawUplift,
                calibration.shorelineUpliftSubunits(),
                calibration.maximumCardinalRiseSubunits());
        capForMountainCeiling(
                upliftCaps,
                baseHeights,
                land,
                calibration.mountainCeilingSubunits());
        clampToCaps(mountainUplift, upliftCaps, land);

        boolean[] mountainInfluence = new boolean[mountainUplift.length];
        long[] result = baseHeights.clone();
        long[] maximumSurface = baseHeights.clone();
        long ceiling = calibration.mountainCeilingSubunits();
        for (int cell = 0; cell < result.length; cell++) {
            if (!land[cell] || mountainUplift[cell] <= 0L) continue;
            mountainInfluence[cell] = true;
            result[cell] = Math.min(ceiling, Math.addExact(baseHeights[cell], mountainUplift[cell]));
            maximumSurface[cell] = Math.min(ceiling, Math.addExact(baseHeights[cell], upliftCaps[cell]));
        }

        // V12 rolling relief is excellent on its own, but inside a much larger mountain its local
        // gradient can add to the mountain gradient and create one-cell voxel terraces. Relax the
        // composed surface itself, just as V12 relaxes authored landforms, rather than trying to
        // infer a concrete shape. Both local bumps and local dips may move inside the footprint;
        // cells outside it remain exact V12 facts.
        relaxComposedMountainSurface(
                result,
                maximumSurface,
                mountainInfluence,
                land,
                width,
                height,
                calibration.maximumCardinalRiseSubunits());

        return new DenseElevationField(bounds, result);
    }

    private static List<MountainSystem> createSystems(
            GenerationRandom random,
            WorldBounds bounds,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        int spacing = calibration.candidateSpacingCells();
        double variation = recipe.widthVariationPpm() / (double) PPM;
        int maximumWidth = Math.max(
                1,
                (int) StrictMath.ceil(calibration.typicalHalfWidthCells() * (1.0 + variation)));
        int maximumLongAxis = Math.max(
                maximumWidth,
                (int) StrictMath.ceil(calibration.typicalLongAxisCells() * (1.0 + variation)));
        int margin = Math.max(1, (maximumLongAxis + spacing - 1) / spacing + 1);

        long minimumLatticeX = Math.floorDiv((long) bounds.minX(), spacing) - margin;
        long maximumLatticeX = Math.floorDiv((long) bounds.maxX(), spacing) + margin;
        long minimumLatticeY = Math.floorDiv((long) bounds.minY(), spacing) - margin;
        long maximumLatticeY = Math.floorDiv((long) bounds.maxY(), spacing) + margin;

        List<MountainSystem> systems = new ArrayList<>();
        for (long latticeY = minimumLatticeY; latticeY <= maximumLatticeY; latticeY++) {
            for (long latticeX = minimumLatticeX; latticeX <= maximumLatticeX; latticeX++) {
                int activation = samplePpm(random, ACTIVE, latticeX, latticeY, 0L);
                if (activation >= calibration.candidateActivationPpm()) continue;
                systems.add(createSystem(random, latticeX, latticeY, calibration, recipe));
            }
        }
        return systems;
    }

    private static MountainSystem createSystem(
            GenerationRandom random,
            long latticeX,
            long latticeY,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        int spacing = calibration.candidateSpacingCells();
        double centerX = latticeX * (double) spacing;
        double centerY = latticeY * (double) spacing;
        double maximumJitter = spacing * recipe.centerJitterPpm() / (double) PPM;
        centerX += centeredPpm(random, CENTER, latticeX, latticeY, 0L) * maximumJitter / PPM;
        centerY += centeredPpm(random, CENTER, latticeX, latticeY, 1L) * maximumJitter / PPM;

        double angle = samplePpm(random, ORIENTATION, latticeX, latticeY, 0L) * TWO_PI / PPM;
        double axisX = StrictMath.cos(angle);
        double axisY = StrictMath.sin(angle);

        double widthVariation = recipe.widthVariationPpm() / (double) PPM;
        double baseWidth = calibration.typicalHalfWidthCells();
        double leftWidth = variedPositive(
                baseWidth,
                centeredPpm(random, WIDTH, latticeX, latticeY, 0L),
                widthVariation);
        double rightWidth = variedPositive(
                baseWidth,
                centeredPpm(random, WIDTH, latticeX, latticeY, 1L),
                widthVariation);
        double longAxisVariation = 1.0
                + centeredPpm(random, WIDTH, latticeX, latticeY, 2L) / (double) PPM
                        * widthVariation * 0.5;
        double longAxis = Math.max(
                Math.max(leftWidth, rightWidth),
                calibration.typicalLongAxisCells() * longAxisVariation);

        double heightVariation = recipe.heightVariationPpm() / (double) PPM;
        double upliftScale = 1.0
                + centeredPpm(random, HEIGHT, latticeX, latticeY, 0L) / (double) PPM
                        * heightVariation;
        long uplift = Math.max(
                0L,
                Math.round(calibration.typicalUpliftSubunits() * upliftScale));

        boolean plateau = calibration.plateausEnabled()
                && samplePpm(random, PLATEAU, latticeX, latticeY, 0L)
                        < calibration.plateauProbabilityPpm();

        return new MountainSystem(
                centerX,
                centerY,
                axisX,
                axisY,
                longAxis,
                leftWidth,
                rightWidth,
                uplift,
                plateau);
    }

    private static void rasterize(
            MountainSystem system,
            WorldBounds bounds,
            int width,
            int height,
            boolean[] land,
            long[] maximumUplift,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        double support = Math.max(
                system.longAxis(),
                Math.max(system.leftWidth(), system.rightWidth()));
        int minX = Math.max(bounds.minX(), (int) StrictMath.floor(system.centerX() - support));
        int maxX = Math.min(bounds.maxX(), (int) StrictMath.ceil(system.centerX() + support));
        int minY = Math.max(bounds.minY(), (int) StrictMath.floor(system.centerY() - support));
        int maxY = Math.min(bounds.maxY(), (int) StrictMath.ceil(system.centerY() + support));
        if (minX > maxX || minY > maxY) return;

        for (int y = minY; y <= maxY; y++) {
            int localY = y - bounds.minY();
            if (localY < 0 || localY >= height) continue;
            for (int x = minX; x <= maxX; x++) {
                int localX = x - bounds.minX();
                if (localX < 0 || localX >= width) continue;
                int cell = localY * width + localX;
                if (!land[cell]) continue;

                double profile = elongatedHillProfile(system, x, y, calibration, recipe);
                if (profile <= 0.0) continue;
                long uplift = Math.max(0L, Math.round(system.upliftSubunits() * profile));
                if (uplift > maximumUplift[cell]) maximumUplift[cell] = uplift;
            }
        }
    }

    /**
     * Broad V12-like hill plus a subordinate smooth inner core. The core increases vertical presence
     * without introducing peak waves, branches or any high-frequency ridge structure.
     */
    private static double elongatedHillProfile(
            MountainSystem system,
            double x,
            double y,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        double dx = x - system.centerX();
        double dy = y - system.centerY();
        double along = dx * system.axisX() + dy * system.axisY();
        double across = -dx * system.axisY() + dy * system.axisX();
        double sideWidth = across < 0.0 ? system.leftWidth() : system.rightWidth();
        if (sideWidth <= 0.0 || system.longAxis() <= 0.0) return 0.0;

        double normalizedAlong = along / system.longAxis();
        double normalizedAcross = across / sideWidth;
        double distanceSquared = normalizedAlong * normalizedAlong
                + normalizedAcross * normalizedAcross;
        if (distanceSquared >= 1.0) return 0.0;

        double sharpness = calibration.sharpnessMilli() / 1_000.0;
        double base = smoothHill(distanceSquared, sharpness, false, recipe.plateauCorePpm());

        double coreRadius = recipe.coreRadiusPpm() / (double) PPM;
        double coreDistanceSquared = distanceSquared / (coreRadius * coreRadius);
        double core = coreDistanceSquared >= 1.0
                ? 0.0
                : smoothHill(
                        coreDistanceSquared,
                        Math.min(1.45, sharpness * 1.05),
                        system.plateau(),
                        recipe.plateauCorePpm());
        double coreWeight = recipe.coreWeightPpm() / (double) PPM;

        return Math.min(1.0, base + coreWeight * core * (1.0 - base));
    }

    private static double smoothHill(
            double distanceSquared,
            double sharpness,
            boolean plateau,
            int plateauCorePpm) {
        double adjustedDistanceSquared = Math.max(0.0, distanceSquared);
        if (plateau) {
            double distance = StrictMath.sqrt(adjustedDistanceSquared);
            double plateauCore = plateauCorePpm / (double) PPM;
            if (distance <= plateauCore) return 1.0;
            double remapped = (distance - plateauCore) / (1.0 - plateauCore);
            adjustedDistanceSquared = remapped * remapped;
        }

        double coordinate = Math.max(0.0, Math.min(1.0, 1.0 - adjustedDistanceSquared));
        double smooth = coordinate * coordinate * (3.0 - 2.0 * coordinate);
        return StrictMath.pow(smooth, sharpness);
    }

    private static int requiredCoastalDistance(
            long maximumUplift,
            long shorelineUplift,
            long maximumRise,
            int minimumDistance) {
        if (maximumUplift <= shorelineUplift) return minimumDistance;
        long riseDistance = (maximumUplift - shorelineUplift + maximumRise - 1L) / maximumRise;
        return Math.max(minimumDistance, Math.toIntExact(Math.min(Integer.MAX_VALUE - 1L, riseDistance + 1L)));
    }

    /** Cardinal distance from ocean/world edge, capped once no mountain uplift needs more room. */
    private static int[] distanceFromOceanCells(
            boolean[] land,
            int width,
            int height,
            int distanceCap) {
        int cap = Math.max(1, distanceCap);
        int[] distance = new int[land.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land[cell]) {
                    distance[cell] = 0;
                } else if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    distance[cell] = 1;
                } else {
                    distance[cell] = cap;
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land[cell]) continue;
                int best = distance[cell];
                if (x > 0) best = Math.min(best, distance[cell - 1] + 1);
                if (y > 0) best = Math.min(best, distance[cell - width] + 1);
                distance[cell] = Math.min(cap, best);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (!land[cell]) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, distance[cell + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[cell + width] + 1);
                distance[cell] = Math.min(cap, best);
            }
        }
        return distance;
    }

    private static long[] coastalUpliftCaps(
            boolean[] land,
            int[] coastalDistance,
            long maximumRawUplift,
            long shorelineUplift,
            long maximumRise) {
        long[] caps = new long[land.length];
        for (int cell = 0; cell < caps.length; cell++) {
            if (!land[cell]) {
                caps[cell] = 0L;
                continue;
            }
            long inlandSteps = Math.max(0, coastalDistance[cell] - 1);
            long permitted = shorelineUplift + inlandSteps * maximumRise;
            caps[cell] = Math.min(maximumRawUplift, permitted);
        }
        return caps;
    }

    private static void capForMountainCeiling(
            long[] caps,
            long[] baseHeights,
            boolean[] land,
            long mountainCeiling) {
        for (int cell = 0; cell < caps.length; cell++) {
            if (!land[cell]) {
                caps[cell] = 0L;
                continue;
            }
            long headroom = Math.max(0L, mountainCeiling - baseHeights[cell]);
            caps[cell] = Math.min(caps[cell], headroom);
        }
    }

    private static void clampToCaps(long[] uplift, long[] caps, boolean[] land) {
        for (int cell = 0; cell < uplift.length; cell++) {
            if (!land[cell]) {
                uplift[cell] = 0L;
            } else if (uplift[cell] > caps[cell]) {
                uplift[cell] = caps[cell];
            }
        }
    }

    /**
     * Bounded V12-style pair relaxation over the composed surface. Alternating scan direction keeps
     * the operation deterministic while allowing local corrections to propagate in every cardinal
     * direction. The broad authored mountain profile means violations are local V12-scale noise;
     * a fixed pass ceiling avoids seed-dependent queue convergence costs on large worlds.
     */
    private static void relaxComposedMountainSurface(
            long[] surface,
            long[] maximumSurface,
            boolean[] adjustable,
            boolean[] land,
            int width,
            int height,
            long maximumRise) {
        for (int pass = 0; pass < MAX_FINAL_SURFACE_RELAXATION_PASSES; pass++) {
            boolean changed = false;
            if ((pass & 1) == 0) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x + 1 < width) {
                            changed |= relaxPair(
                                    cell,
                                    cell + 1,
                                    surface,
                                    maximumSurface,
                                    adjustable,
                                    land,
                                    maximumRise);
                        }
                        if (y + 1 < height) {
                            changed |= relaxPair(
                                    cell,
                                    cell + width,
                                    surface,
                                    maximumSurface,
                                    adjustable,
                                    land,
                                    maximumRise);
                        }
                    }
                }
            } else {
                for (int y = height - 1; y >= 0; y--) {
                    for (int x = width - 1; x >= 0; x--) {
                        int cell = y * width + x;
                        if (!land[cell]) continue;
                        if (x > 0) {
                            changed |= relaxPair(
                                    cell,
                                    cell - 1,
                                    surface,
                                    maximumSurface,
                                    adjustable,
                                    land,
                                    maximumRise);
                        }
                        if (y > 0) {
                            changed |= relaxPair(
                                    cell,
                                    cell - width,
                                    surface,
                                    maximumSurface,
                                    adjustable,
                                    land,
                                    maximumRise);
                        }
                    }
                }
            }
            if (!changed) return;
        }
    }

    private static boolean relaxPair(
            int first,
            int second,
            long[] surface,
            long[] maximumSurface,
            boolean[] adjustable,
            boolean[] land,
            long maximumRise) {
        if (!land[first] || !land[second]) return false;
        long firstHeight = surface[first];
        long secondHeight = surface[second];
        long difference = firstHeight - secondHeight;
        if (absolute(difference) <= maximumRise) return false;

        int high = difference > 0L ? first : second;
        int low = difference > 0L ? second : first;
        boolean highAdjustable = adjustable[high];
        boolean lowAdjustable = adjustable[low];
        if (!highAdjustable && !lowAdjustable) return false;

        long excess = surface[high] - surface[low] - maximumRise;
        long highDownCapacity = highAdjustable ? Math.max(0L, surface[high] - 1L) : 0L;
        long lowUpCapacity = lowAdjustable
                ? Math.max(0L, maximumSurface[low] - surface[low])
                : 0L;
        if (highDownCapacity + lowUpCapacity <= 0L) return false;

        long down = 0L;
        long up = 0L;
        if (highAdjustable && lowAdjustable) {
            down = Math.min(highDownCapacity, (excess + 1L) / 2L);
            up = Math.min(lowUpCapacity, excess - down);
            long remaining = excess - down - up;
            if (remaining > 0L) {
                long extraDown = Math.min(highDownCapacity - down, remaining);
                down += extraDown;
                remaining -= extraDown;
            }
            if (remaining > 0L) {
                up += Math.min(lowUpCapacity - up, remaining);
            }
        } else if (highAdjustable) {
            down = Math.min(highDownCapacity, excess);
        } else {
            up = Math.min(lowUpCapacity, excess);
        }

        if (down <= 0L && up <= 0L) return false;
        surface[high] -= down;
        surface[low] += up;
        return true;
    }

    /** Normalized low-pass smoothing removes max-composition seams without damping coast cells. */
    private static void smoothUplift(
            long[] uplift,
            boolean[] land,
            int width,
            int height,
            int passes) {
        if (passes <= 0) return;
        long[] scratch = new long[uplift.length];
        for (int pass = 0; pass < passes; pass++) {
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int cell = y * width + x;
                    if (!land[cell]) {
                        scratch[cell] = 0L;
                        continue;
                    }
                    long sum = uplift[cell] * 4L;
                    int weight = 4;
                    if (x > 0 && land[cell - 1]) {
                        sum += uplift[cell - 1];
                        weight++;
                    }
                    if (x + 1 < width && land[cell + 1]) {
                        sum += uplift[cell + 1];
                        weight++;
                    }
                    if (y > 0 && land[cell - width]) {
                        sum += uplift[cell - width];
                        weight++;
                    }
                    if (y + 1 < height && land[cell + width]) {
                        sum += uplift[cell + width];
                        weight++;
                    }
                    scratch[cell] = sum / weight;
                }
            }
            System.arraycopy(scratch, 0, uplift, 0, uplift.length);
        }
    }

    private static long maximum(long[] values) {
        long maximum = 0L;
        for (long value : values) maximum = Math.max(maximum, value);
        return maximum;
    }

    private static long absolute(long value) {
        if (value == Long.MIN_VALUE) {
            throw new ArithmeticException("surface difference exceeds signed range");
        }
        return Math.abs(value);
    }

    private static boolean sameHorizontalBounds(WorldBounds first, WorldBounds second) {
        return first.minX() == second.minX()
                && first.maxX() == second.maxX()
                && first.minY() == second.minY()
                && first.maxY() == second.maxY();
    }

    private static double variedPositive(double base, int centeredCoordinatePpm, double variation) {
        return Math.max(1.0, base * (1.0 + centeredCoordinatePpm / (double) PPM * variation));
    }

    private static int samplePpm(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        long unsignedHigh = random.sampleLong(STAGE_ID, purpose, x, y, 0L, ordinal) >>> 32;
        return (int) (unsignedHigh * PPM / 0x1_0000_0000L);
    }

    private static int centeredPpm(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        return samplePpm(random, purpose, x, y, ordinal) * 2 - PPM;
    }

    private record MountainSystem(
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double longAxis,
            double leftWidth,
            double rightWidth,
            long upliftSubunits,
            boolean plateau) {
    }
}
