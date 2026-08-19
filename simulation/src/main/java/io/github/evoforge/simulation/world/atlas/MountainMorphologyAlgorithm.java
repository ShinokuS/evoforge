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
 * <p>Each mountain is born as one bounded-slope anisotropic envelope above the accepted V12 terrain.
 * Its actual uplift is chosen first, then its axes are made wide enough for that uplift before any
 * rasterization occurs. A sampled source that falls on water is resolved deterministically to the
 * nearest accepted V12 land inside that mountain's own source radius before the envelope is built.
 * The envelope is allowed to keep descending past its nominal anchor level until it naturally falls
 * below the existing terrain, so there is no hard terrace edge to repair. Multiple systems compose
 * by maximum height. There is deliberately no post-generation widening, smoothing, terrace cleanup,
 * or Shape-aware mountain repair.</p>
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
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(random, bounds, calibration, recipe)) {
            rasterize(
                    system,
                    bounds,
                    width,
                    height,
                    baseHeights,
                    land,
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
        int requiredHalfWidth = Math.toIntExact(Math.max(
                1L,
                uplift == 0L ? 1L : (uplift + maximumRise - 1L) / maximumRise));

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
            MountainSystem sampledSystem,
            WorldBounds bounds,
            int width,
            int height,
            long[] baseHeights,
            boolean[] land,
            long[] result,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        if (sampledSystem.upliftSubunits() <= 0L) return;

        MountainSystem system = anchorToNearestLand(sampledSystem, bounds, width, height, land);
        if (system == null) return;

        int anchorX = (int) system.centerX();
        int anchorY = (int) system.centerY();
        int anchorCell = (anchorY - bounds.minY()) * width + (anchorX - bounds.minX());
        long anchorHeight = baseHeights[anchorCell];
        long rawPeak = Math.min(
                calibration.mountainCeilingSubunits(),
                Math.addExact(anchorHeight, system.upliftSubunits()));
        long peakHeight = stabilizeSummitBand(
                rawPeak,
                anchorHeight,
                calibration.maximumCardinalRiseSubunits());
        long effectiveUplift = peakHeight - anchorHeight;
        if (effectiveUplift <= 0L) return;

        // The nominal axes describe the climb from the local summit anchor to its base level. The
        // same linear envelope continues below that level until it reaches sea level, preventing a
        // hard ring where local terrain happens to sit below the anchor height.
        double supportFactor = peakHeight / (double) effectiveUplift;
        double support = Math.max(
                system.longAxis(),
                Math.max(system.leftWidth(), system.rightWidth())) * supportFactor;
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

                double profile = boundedProfile(system, x, y, recipe);
                double candidateDouble = anchorHeight + effectiveUplift * profile;
                if (!(candidateDouble > 0.0)) continue;
                long candidate = Math.min(
                        calibration.mountainCeilingSubunits(),
                        Math.round(candidateDouble));
                if (candidate > result[cell]) result[cell] = candidate;
            }
        }
    }

    /**
     * Resolve a sampled source onto the accepted V12 land before generating anything. This is part
     * of source placement, not a repair of generated terrain: the mountain envelope is constructed
     * only after its final center is known. Sources outside the requested world are not pulled in.
     */
    private static MountainSystem anchorToNearestLand(
            MountainSystem sampled,
            WorldBounds bounds,
            int width,
            int height,
            boolean[] land) {
        if (sampled.centerX() < bounds.minX() || sampled.centerX() > bounds.maxX()
                || sampled.centerY() < bounds.minY() || sampled.centerY() > bounds.maxY()) {
            return null;
        }

        int searchRadius = Math.max(
                1,
                (int) StrictMath.ceil(Math.max(sampled.leftWidth(), sampled.rightWidth())));
        int minX = Math.max(bounds.minX(), (int) StrictMath.floor(sampled.centerX() - searchRadius));
        int maxX = Math.min(bounds.maxX(), (int) StrictMath.ceil(sampled.centerX() + searchRadius));
        int minY = Math.max(bounds.minY(), (int) StrictMath.floor(sampled.centerY() - searchRadius));
        int maxY = Math.min(bounds.maxY(), (int) StrictMath.ceil(sampled.centerY() + searchRadius));
        double maximumDistanceSquared = searchRadius * (double) searchRadius;

        boolean found = false;
        int bestX = 0;
        int bestY = 0;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (int y = minY; y <= maxY; y++) {
            int localY = y - bounds.minY();
            if (localY < 0 || localY >= height) continue;
            for (int x = minX; x <= maxX; x++) {
                int localX = x - bounds.minX();
                if (localX < 0 || localX >= width || !land[localY * width + localX]) continue;

                double dx = x - sampled.centerX();
                double dy = y - sampled.centerY();
                double distanceSquared = dx * dx + dy * dy;
                if (distanceSquared > maximumDistanceSquared) continue;
                if (!found
                        || distanceSquared < bestDistanceSquared
                        || (Double.compare(distanceSquared, bestDistanceSquared) == 0
                                && (y < bestY || (y == bestY && x < bestX)))) {
                    found = true;
                    bestX = x;
                    bestY = y;
                    bestDistanceSquared = distanceSquared;
                }
            }
        }
        if (!found) return null;

        return new MountainSystem(
                bestX,
                bestY,
                sampled.axisX(),
                sampled.axisY(),
                sampled.longAxis(),
                sampled.leftWidth(),
                sampled.rightWidth(),
                sampled.upliftSubunits(),
                sampled.plateau());
    }

    /**
     * Linear radial descent has a bounded derivative everywhere, including outside the nominal
     * radius. Plateau systems reserve a proportionally wider footprint first, so their outer descent
     * obeys exactly the same source slope law.
     */
    private static double boundedProfile(
            MountainSystem system,
            double x,
            double y,
            MountainRecipe recipe) {
        double radius = normalizedRadius(system, x, y);
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

    private static long stabilizeSummitBand(long peakHeight, long anchorHeight, long maximumRise) {
        long cell = ElevationField.SUBUNITS_PER_CELL;
        long minimumDepth = Math.min(cell - 1L, maximumRise * MINIMUM_SUMMIT_BAND_CELLS);
        long layer = Math.floorDiv(peakHeight, cell);
        long layerFloor = Math.multiplyExact(layer, cell);
        long fraction = peakHeight - layerFloor;
        if (fraction >= minimumDepth) return peakHeight;

        long lowered = layerFloor - (cell - minimumDepth);
        return lowered > anchorHeight ? lowered : peakHeight;
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
