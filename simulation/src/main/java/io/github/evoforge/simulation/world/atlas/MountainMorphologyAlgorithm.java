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
 * Deterministic source synthesis for V13 mountain structure.
 *
 * <p>Mountains are generated with their final broad surface geometry from the start. Each system is
 * an anisotropic bounded-slope envelope over the accepted V12 land. System width is coupled directly
 * to its complete absolute climb from ordinary land to its intended summit, so discrete vertical
 * levels are broad before any later Shape fitting happens. Multiple systems are composed by maximum
 * height; there is no after-the-fact terrace widening, slope repair, morphology cleanup, or
 * Shape-aware mountain pass.</p>
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
    private static final int MINIMUM_SUMMIT_BAND_CELLS = 3;

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

        long[] result = baseHeights.clone();
        int[] coastalDistance = distanceFromOceanCells(land, width, height);
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(random, bounds, calibration, recipe)) {
            rasterize(
                    system,
                    bounds,
                    width,
                    height,
                    baseHeights,
                    land,
                    coastalDistance,
                    result,
                    calibration,
                    recipe);
        }
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

        double heightVariation = recipe.heightVariationPpm() / (double) PPM;
        double upliftScale = 1.0
                + centeredPpm(random, HEIGHT, latticeX, latticeY, 0L) / (double) PPM
                        * heightVariation;
        long uplift = Math.max(0L, Math.round(calibration.typicalUpliftSubunits() * upliftScale));

        long maximumRise = calibration.maximumCardinalRiseSubunits();
        long absolutePeakAboveSea = Math.min(
                calibration.mountainCeilingSubunits(),
                Math.addExact(calibration.baseTerrainCeilingSubunits(), uplift));
        int requiredHalfWidth = Math.toIntExact(Math.max(
                1L,
                (absolutePeakAboveSea + maximumRise - 1L) / maximumRise));

        double widthVariation = recipe.widthVariationPpm() / (double) PPM;
        double baseWidth = calibration.typicalHalfWidthCells();
        double leftWidth = Math.max(
                requiredHalfWidth,
                variedPositive(baseWidth, centeredPpm(random, WIDTH, latticeX, latticeY, 0L), widthVariation));
        double rightWidth = Math.max(
                requiredHalfWidth,
                variedPositive(baseWidth, centeredPpm(random, WIDTH, latticeX, latticeY, 1L), widthVariation));
        double longAxisVariation = 1.0
                + centeredPpm(random, WIDTH, latticeX, latticeY, 2L) / (double) PPM
                        * widthVariation * 0.5;
        double longAxis = Math.max(
                Math.max(leftWidth, rightWidth),
                Math.max(requiredHalfWidth, calibration.typicalLongAxisCells() * longAxisVariation));

        boolean plateau = calibration.plateausEnabled()
                && samplePpm(random, PLATEAU, latticeX, latticeY, 0L)
                        < calibration.plateauProbabilityPpm();
        if (plateau) {
            double descentFraction = 1.0 - recipe.plateauCorePpm() / (double) PPM;
            double widthScale = 1.0 / descentFraction;
            leftWidth *= widthScale;
            rightWidth *= widthScale;
            longAxis *= widthScale;
        }

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
            long[] baseHeights,
            boolean[] land,
            int[] coastalDistance,
            long[] result,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        if (system.upliftSubunits() <= 0L) return;
        double support = Math.max(
                system.longAxis(),
                Math.max(system.leftWidth(), system.rightWidth()));
        int minX = Math.max(bounds.minX(), (int) StrictMath.floor(system.centerX() - support));
        int maxX = Math.min(bounds.maxX(), (int) StrictMath.ceil(system.centerX() + support));
        int minY = Math.max(bounds.minY(), (int) StrictMath.floor(system.centerY() - support));
        int maxY = Math.min(bounds.maxY(), (int) StrictMath.ceil(system.centerY() + support));
        if (minX > maxX || minY > maxY) return;

        long footHeight = regionalFootHeight(
                system,
                bounds,
                width,
                height,
                baseHeights,
                land,
                minX,
                maxX,
                minY,
                maxY);
        if (footHeight == Long.MAX_VALUE) return;

        // Mountain height is authored in the V13 headroom above ordinary V12 terrain. The absolute
        // summit therefore does not collapse merely because this particular system happens to touch
        // low land. Width was already reserved against the more conservative sea-level climb.
        long rawPeak = Math.min(
                calibration.mountainCeilingSubunits(),
                Math.addExact(calibration.baseTerrainCeilingSubunits(), system.upliftSubunits()));
        long peakHeight = stabilizeSummitBand(
                rawPeak,
                footHeight,
                calibration.maximumCardinalRiseSubunits());
        long effectiveUplift = peakHeight - footHeight;
        if (effectiveUplift <= 0L) return;

        for (int y = minY; y <= maxY; y++) {
            int localY = y - bounds.minY();
            if (localY < 0 || localY >= height) continue;
            for (int x = minX; x <= maxX; x++) {
                int localX = x - bounds.minX();
                if (localX < 0 || localX >= width) continue;
                int cell = localY * width + localX;
                if (!land[cell]) continue;

                double profile = boundedProfile(system, x, y, recipe);
                if (profile <= 0.0) continue;
                long rise = Math.max(0L, Math.round(effectiveUplift * profile));
                long coastalCap = coastalRiseCap(cell, coastalDistance, calibration, effectiveUplift);
                rise = Math.min(rise, coastalCap);
                long candidate = Math.addExact(footHeight, rise);
                if (candidate > result[cell]) result[cell] = candidate;
            }
        }
    }

    private static long regionalFootHeight(
            MountainSystem system,
            WorldBounds bounds,
            int width,
            int height,
            long[] baseHeights,
            boolean[] land,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        long minimum = Long.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            int localY = y - bounds.minY();
            if (localY < 0 || localY >= height) continue;
            for (int x = minX; x <= maxX; x++) {
                int localX = x - bounds.minX();
                if (localX < 0 || localX >= width) continue;
                int cell = localY * width + localX;
                if (!land[cell] || normalizedRadius(system, x, y) >= 1.0) continue;
                minimum = Math.min(minimum, baseHeights[cell]);
            }
        }
        return minimum;
    }

    /**
     * Linear radial descent has a bounded derivative everywhere. Plateau systems simply reserve a
     * wider source footprint before applying the same descent, so their outer slope obeys the same
     * source-generation budget instead of being repaired afterwards.
     */
    private static double boundedProfile(
            MountainSystem system,
            double x,
            double y,
            MountainRecipe recipe) {
        double radius = normalizedRadius(system, x, y);
        if (radius >= 1.0) return 0.0;
        if (!system.plateau()) return 1.0 - radius;

        double plateauCore = recipe.plateauCorePpm() / (double) PPM;
        if (radius <= plateauCore) return 1.0;
        return (1.0 - radius) / (1.0 - plateauCore);
    }

    private static double normalizedRadius(MountainSystem system, double x, double y) {
        double dx = x - system.centerX();
        double dy = y - system.centerY();
        double along = dx * system.axisX() + dy * system.axisY();
        double across = -dx * system.axisY() + dy * system.axisX();
        double sideWidth = across < 0.0 ? system.leftWidth() : system.rightWidth();
        if (sideWidth <= 0.0 || system.longAxis() <= 0.0) return Double.POSITIVE_INFINITY;
        double normalizedAlong = along / system.longAxis();
        double normalizedAcross = across / sideWidth;
        return StrictMath.sqrt(normalizedAlong * normalizedAlong + normalizedAcross * normalizedAcross);
    }

    private static long stabilizeSummitBand(long peakHeight, long footHeight, long maximumRise) {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        long minimumDepth = Math.min(cell - 1L, maximumRise * MINIMUM_SUMMIT_BAND_CELLS);
        long layer = Math.floorDiv(peakHeight, cell);
        long layerFloor = Math.multiplyExact(layer, cell);
        long fraction = peakHeight - layerFloor;
        if (fraction >= minimumDepth) return peakHeight;

        long lowered = layerFloor - (cell - minimumDepth);
        return lowered > footHeight ? lowered : peakHeight;
    }

    private static long coastalRiseCap(
            int cell,
            int[] coastalDistance,
            MountainCalibration calibration,
            long effectiveUplift) {
        long inlandSteps = Math.max(0, coastalDistance[cell] - 1);
        long permitted = calibration.shorelineUpliftSubunits()
                + inlandSteps * calibration.maximumCardinalRiseSubunits();
        return Math.min(effectiveUplift, permitted);
    }

    /** Exact cardinal distance from ocean/world edge. */
    private static int[] distanceFromOceanCells(boolean[] land, int width, int height) {
        int infinity = Math.addExact(width, height) + 1;
        int[] distance = new int[land.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land[cell]) {
                    distance[cell] = 0;
                } else if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    distance[cell] = 1;
                } else {
                    distance[cell] = infinity;
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
                distance[cell] = best;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (!land[cell]) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, distance[cell + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[cell + width] + 1);
                distance[cell] = best;
            }
        }
        return distance;
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
