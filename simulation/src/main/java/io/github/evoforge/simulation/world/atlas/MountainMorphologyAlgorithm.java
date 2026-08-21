package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.GenerationStageId;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic spatial synthesis for V13 structural mountains.
 *
 * <p>Each source is one asymmetric elongated hill. Abundance owns the expected amount of land
 * occupied by mountain structures, while scale and chaininess own their individual size and
 * elongation. Height is capped by what that footprint can support at the calibrated geometric
 * slope. The algorithm knows nothing about concrete runtime Shapes.</p>
 */
final class MountainMorphologyAlgorithm implements MountainElevationAlgorithm {
    private static final GenerationStageId STAGE_ID = GenerationStageId.of("world:mountains");
    private static final GenerationPurposeId ACTIVE = GenerationPurposeId.of("mountain:active");
    private static final GenerationPurposeId CENTER = GenerationPurposeId.of("mountain:center");
    private static final GenerationPurposeId ORIENTATION = GenerationPurposeId.of("mountain:orientation");
    private static final GenerationPurposeId WIDTH = GenerationPurposeId.of("mountain:width");
    private static final GenerationPurposeId HEIGHT = GenerationPurposeId.of("mountain:height");
    private static final GenerationPurposeId PLATEAU = GenerationPurposeId.of("mountain:plateau");

    private static final int PPM = NormalizedValue.SCALE;
    private static final double TWO_PI = StrictMath.PI * 2.0;
    private static final double MEAN_VISIBLE_FOOTPRINT_FRACTION = 0.72;

    @Override
    public ElevationField generate(
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
        int landCount = 0;
        int index = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                long value = base.elevationSubunitsAt(x, y);
                baseHeights[index] = value;
                land[index] = value > ElevationGenerationStage.SEA_LEVEL_SUBUNITS;
                if (land[index]) landCount++;
                index++;
            }
        }

        if (calibration.targetCoveragePpm() == 0
                || calibration.typicalUpliftSubunits() == 0L
                || landCount == 0) {
            return DenseElevationField.takeOwnership(bounds, baseHeights);
        }

        long[] mountainUplift = new long[calibration.area()];
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(
                random, bounds, land, landCount, width, height, calibration, recipe)) {
            rasterize(system, bounds, width, height, land, mountainUplift, calibration, recipe);
        }

        long maximumRawUplift = maximum(mountainUplift);
        if (maximumRawUplift > 0L) {
            int[] coastalDistance = distanceFromOceanCells(
                    land,
                    width,
                    height,
                    calibration.coastalTransitionCells() + 1);
            applyCoastalCap(
                    mountainUplift,
                    land,
                    coastalDistance,
                    maximumRawUplift,
                    calibration.shorelineUpliftSubunits(),
                    calibration.coastalTransitionCells(),
                    calibration.maximumCardinalRiseSubunits());
        }

        long[] result = baseHeights.clone();
        long ceiling = calibration.mountainCeilingSubunits();
        for (int cell = 0; cell < result.length; cell++) {
            if (!land[cell] || mountainUplift[cell] <= 0L) continue;
            result[cell] = Math.min(ceiling, Math.addExact(result[cell], mountainUplift[cell]));
        }
        return DenseElevationField.takeOwnership(bounds, result);
    }

    /**
     * Builds a deterministic ranked candidate set and selects a source count from the requested
     * coverage budget. Abundance therefore describes real structure coverage rather than a raw
     * per-node Bernoulli probability.
     */
    private static List<MountainSystem> createSystems(
            GenerationRandom random,
            WorldBounds bounds,
            boolean[] land,
            int landCount,
            int width,
            int height,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        int spacing = calibration.candidateSpacingCells();
        long minimumLatticeX = Math.floorDiv((long) bounds.minX(), spacing) - 1L;
        long maximumLatticeX = Math.floorDiv((long) bounds.maxX(), spacing) + 1L;
        long minimumLatticeY = Math.floorDiv((long) bounds.minY(), spacing) - 1L;
        long maximumLatticeY = Math.floorDiv((long) bounds.maxY(), spacing) + 1L;

        List<MountainCandidate> candidates = new ArrayList<>();
        for (long latticeY = minimumLatticeY; latticeY <= maximumLatticeY; latticeY++) {
            for (long latticeX = minimumLatticeX; latticeX <= maximumLatticeX; latticeX++) {
                MountainSystem system = createSystem(random, latticeX, latticeY, calibration, recipe);
                if (!centerIsLand(system, bounds, land, width, height)) continue;
                int priority = samplePpm(random, ACTIVE, latticeX, latticeY, 0L);
                candidates.add(new MountainCandidate(priority, latticeX, latticeY, system));
            }
        }
        if (candidates.isEmpty()) return List.of();

        candidates.sort(Comparator
                .comparingInt(MountainCandidate::priority)
                .thenComparingLong(MountainCandidate::latticeY)
                .thenComparingLong(MountainCandidate::latticeX));

        int desiredSources = desiredSourceCount(landCount, calibration);
        List<MountainSystem> selected = new ArrayList<>(Math.min(desiredSources, candidates.size()));
        double minimumCenterDistance = calibration.typicalHalfWidthCells() * 1.20;
        double minimumCenterDistanceSquared = minimumCenterDistance * minimumCenterDistance;

        for (MountainCandidate candidate : candidates) {
            if (selected.size() >= desiredSources) break;
            if (tooCloseToSelected(candidate.system(), selected, minimumCenterDistanceSquared)) continue;
            selected.add(candidate.system());
        }

        // Small worlds or fragmented coastlines can leave too few well-separated centers. Fill the
        // remaining quota from the same deterministic ranking; max composition still prevents
        // overlap from creating additive spikes.
        if (selected.size() < desiredSources) {
            for (MountainCandidate candidate : candidates) {
                if (selected.size() >= desiredSources) break;
                if (selected.contains(candidate.system())) continue;
                selected.add(candidate.system());
            }
        }
        return selected;
    }

    private static int desiredSourceCount(int landCount, MountainCalibration calibration) {
        double targetCells = (double) landCount * calibration.targetCoveragePpm() / PPM;
        double nominalFootprint = StrictMath.PI
                * calibration.typicalHalfWidthCells()
                * (double) calibration.typicalLongAxisCells()
                * MEAN_VISIBLE_FOOTPRINT_FRACTION;
        if (targetCells <= 0.0 || nominalFootprint <= 0.0) return 0;
        return Math.max(1, (int) StrictMath.ceil(targetCells / nominalFootprint));
    }

    private static boolean tooCloseToSelected(
            MountainSystem candidate,
            List<MountainSystem> selected,
            double minimumDistanceSquared) {
        for (MountainSystem existing : selected) {
            double dx = candidate.centerX() - existing.centerX();
            double dy = candidate.centerY() - existing.centerY();
            if (dx * dx + dy * dy < minimumDistanceSquared) return true;
        }
        return false;
    }

    private static boolean centerIsLand(
            MountainSystem system,
            WorldBounds bounds,
            boolean[] land,
            int width,
            int height) {
        int x = (int) StrictMath.round(system.centerX());
        int y = (int) StrictMath.round(system.centerY());
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        if (localX < 0 || localX >= width || localY < 0 || localY >= height) return false;
        return land[localY * width + localX];
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

        double longVariation = widthVariation * 0.72;
        double negativeLongAxis = variedPositive(
                calibration.typicalLongAxisCells(),
                centeredPpm(random, WIDTH, latticeX, latticeY, 2L),
                longVariation);
        double positiveLongAxis = variedPositive(
                calibration.typicalLongAxisCells(),
                centeredPpm(random, WIDTH, latticeX, latticeY, 3L),
                longVariation);
        double minimumLong = Math.max(leftWidth, rightWidth) * 0.92;
        negativeLongAxis = Math.max(minimumLong, negativeLongAxis);
        positiveLongAxis = Math.max(minimumLong, positiveLongAxis);

        boolean plateau = calibration.plateausEnabled()
                && samplePpm(random, PLATEAU, latticeX, latticeY, 0L)
                        < calibration.plateauProbabilityPpm();

        double heightVariation = recipe.heightVariationPpm() / (double) PPM;
        double upliftScale = 1.0
                + centeredPpm(random, HEIGHT, latticeX, latticeY, 0L) / (double) PPM
                        * heightVariation;
        long requestedUplift = Math.max(
                0L,
                Math.round(calibration.typicalUpliftSubunits() * upliftScale));
        double narrowAxis = Math.max(
                1.0,
                Math.min(
                        Math.min(leftWidth, rightWidth),
                        Math.min(negativeLongAxis, positiveLongAxis)));
        double gradientBound = recipe.profileGradientBound(plateau);
        long supportedUplift = Math.max(
                0L,
                (long) StrictMath.floor(
                        narrowAxis * calibration.maximumCardinalRiseSubunits() / gradientBound));
        long uplift = Math.min(requestedUplift, supportedUplift);

        return new MountainSystem(
                centerX,
                centerY,
                axisX,
                axisY,
                negativeLongAxis,
                positiveLongAxis,
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
                Math.max(system.negativeLongAxis(), system.positiveLongAxis()),
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

        double longAxis = along < 0.0 ? system.negativeLongAxis() : system.positiveLongAxis();
        double sideWidth = across < 0.0 ? system.leftWidth() : system.rightWidth();
        if (longAxis <= 0.0 || sideWidth <= 0.0) return 0.0;

        double normalizedAlong = along / longAxis;
        double normalizedAcross = across / sideWidth;
        double radius = StrictMath.hypot(normalizedAlong, normalizedAcross);
        if (radius >= 1.0) return 0.0;

        double sharpness = calibration.sharpnessMilli() / 1_000.0;
        return layeredHill(radius, sharpness, system.plateau(), recipe.plateauCorePpm());
    }

    /**
     * Layer-friendly radial profile: a long near-linear middle slope with smooth summit and foot.
     * The derivative bound is recipe-owned so calibration and synthesis use the same model policy.
     */
    private static double layeredHill(
            double radius,
            double sharpness,
            boolean plateau,
            int plateauCorePpm) {
        double r = Math.max(0.0, Math.min(1.0, radius));
        if (plateau) {
            double plateauCore = plateauCorePpm / (double) PPM;
            if (r <= plateauCore) return 1.0;
            r = (r - plateauCore) / (1.0 - plateauCore);
        }

        double character = Math.max(0.0, Math.min(1.0, (sharpness - 0.85) / 0.40));
        double summitEase = 0.22 - character * 0.12;
        double footEase = 0.14;
        double slope = 1.0 / (1.0 - (summitEase + footEase) * 0.5);

        if (r < summitEase) {
            return Math.max(0.0, 1.0 - slope * r * r / (2.0 * summitEase));
        }
        if (r <= 1.0 - footEase) {
            double summitValue = 1.0 - slope * summitEase * 0.5;
            return Math.max(0.0, summitValue - slope * (r - summitEase));
        }
        double remaining = 1.0 - r;
        return Math.max(0.0, slope * remaining * remaining / (2.0 * footEase));
    }

    /** Cardinal distance from ocean/world edge, capped once the coastal transition is fully inland. */
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

    /**
     * Caps mountain uplift near the coast with another cardinal-Lipschitz field.
     *
     * <p>Taking {@code min(rawMountain, coastalCap)} preserves the same rise bound. This is unlike
     * multiplying a sloped mountain by a sloped fade, where both gradients add and can exceed the
     * calibrated budget even when each input is individually smooth.</p>
     */
    private static void applyCoastalCap(
            long[] uplift,
            boolean[] land,
            int[] coastalDistance,
            long maximumUplift,
            long shorelineUplift,
            int transitionCells,
            long maximumCardinalRise) {
        if (maximumUplift <= 0L) return;

        int transition = Math.max(1, transitionCells);
        long shoreline = Math.max(
                0L,
                Math.min(maximumUplift, Math.min(shorelineUplift, maximumCardinalRise)));
        long inlandRise = maximumUplift - shoreline;
        long risePerCell = inlandRise == 0L
                ? 0L
                : (inlandRise + transition - 1L) / transition;
        risePerCell = Math.min(maximumCardinalRise, risePerCell);

        for (int cell = 0; cell < uplift.length; cell++) {
            if (!land[cell] || uplift[cell] <= 0L) continue;
            long inlandSteps = Math.max(0L, (long) coastalDistance[cell] - 1L);
            long allowedRise = Math.min(
                    inlandRise,
                    Math.multiplyExact(inlandSteps, risePerCell));
            long cap = Math.addExact(shoreline, allowedRise);
            uplift[cell] = Math.min(uplift[cell], cap);
        }
    }

    private static long maximum(long[] values) {
        long maximum = 0L;
        for (long value : values) maximum = Math.max(maximum, value);
        return maximum;
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

    private record MountainCandidate(
            int priority,
            long latticeX,
            long latticeY,
            MountainSystem system) {
    }

    private record MountainSystem(
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double negativeLongAxis,
            double positiveLongAxis,
            double leftWidth,
            double rightWidth,
            long upliftSubunits,
            boolean plateau) {
    }
}
