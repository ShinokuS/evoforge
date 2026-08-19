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
 * Deterministic spatial synthesis for V13 structural mountains.
 *
 * <p>Each source is authored as one asymmetric elongated mass. Abundance controls how many source
 * footprints are expected to occupy land; scale and chaininess control the footprint geometry; and
 * height is capped by what that authored footprint can support at the requested geometric slope.
 * The stage knows nothing about concrete runtime Shapes.</p>
 */
final class MountainMorphologyAlgorithm {
    private static final GenerationStageId STAGE_ID = GenerationStageId.of("world:mountains");
    private static final GenerationPurposeId ACTIVE = GenerationPurposeId.of("mountain:active");
    private static final GenerationPurposeId CENTER = GenerationPurposeId.of("mountain:center");
    private static final GenerationPurposeId ORIENTATION = GenerationPurposeId.of("mountain:orientation");
    private static final GenerationPurposeId WIDTH = GenerationPurposeId.of("mountain:width");
    private static final GenerationPurposeId HEIGHT = GenerationPurposeId.of("mountain:height");
    private static final GenerationPurposeId PLATEAU = GenerationPurposeId.of("mountain:plateau");
    private static final GenerationPurposeId CORE = GenerationPurposeId.of("mountain:core-offset");

    private static final int PPM = NormalizedValue.SCALE;
    private static final double TWO_PI = StrictMath.PI * 2.0;
    private static final double PROFILE_GRADIENT_BOUND = 1.30;
    private static final double PLATEAU_PROFILE_GRADIENT_BOUND = 1.60;
    private static final long MINIMUM_VISIBLE_UPLIFT_SUBUNITS = ElevationField.SUBUNITS_PER_CELL / 10L;

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
        for (MountainSystem system : createSystems(
                random, bounds, land, width, height, calibration, recipe)) {
            rasterize(system, bounds, width, height, land, mountainUplift, calibration, recipe);
        }

        smoothUplift(mountainUplift, land, width, height, recipe.upliftSmoothingPasses());
        removeInvisibleFringe(mountainUplift);

        long maximumRawUplift = maximum(mountainUplift);
        if (maximumRawUplift > 0L) {
            int[] coastalDistance = distanceFromOceanCells(
                    land,
                    width,
                    height,
                    calibration.coastalTransitionCells() + 1);
            applyCoastalFade(
                    mountainUplift,
                    land,
                    coastalDistance,
                    maximumRawUplift,
                    calibration.shorelineUpliftSubunits(),
                    calibration.coastalTransitionCells());
            removeInvisibleFringe(mountainUplift);
        }

        long[] result = baseHeights.clone();
        long ceiling = calibration.mountainCeilingSubunits();
        for (int cell = 0; cell < result.length; cell++) {
            if (!land[cell] || mountainUplift[cell] <= 0L) continue;
            result[cell] = Math.min(ceiling, Math.addExact(result[cell], mountainUplift[cell]));
        }
        return new DenseElevationField(bounds, result);
    }

    private static List<MountainSystem> createSystems(
            GenerationRandom random,
            WorldBounds bounds,
            boolean[] land,
            int width,
            int height,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        int spacing = calibration.candidateSpacingCells();
        double variation = recipe.widthVariationPpm() / (double) PPM;
        int maximumSupport = Math.max(
                1,
                (int) StrictMath.ceil(
                        calibration.typicalLongAxisCells() * (1.0 + variation)));
        int margin = Math.max(1, (maximumSupport + spacing - 1) / spacing + 1);

        long minimumLatticeX = Math.floorDiv((long) bounds.minX(), spacing) - margin;
        long maximumLatticeX = Math.floorDiv((long) bounds.maxX(), spacing) + margin;
        long minimumLatticeY = Math.floorDiv((long) bounds.minY(), spacing) - margin;
        long maximumLatticeY = Math.floorDiv((long) bounds.maxY(), spacing) + margin;

        List<MountainSystem> systems = new ArrayList<>();
        for (long latticeY = minimumLatticeY; latticeY <= maximumLatticeY; latticeY++) {
            for (long latticeX = minimumLatticeX; latticeX <= maximumLatticeX; latticeX++) {
                int activation = samplePpm(random, ACTIVE, latticeX, latticeY, 0L);
                if (activation >= calibration.candidateActivationPpm()) continue;
                MountainSystem system = createSystem(random, latticeX, latticeY, calibration, recipe);
                if (!centerIsLand(system, bounds, land, width, height)) continue;
                systems.add(system);
            }
        }
        return systems;
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
        double gradientBound = plateau
                ? PLATEAU_PROFILE_GRADIENT_BOUND
                : PROFILE_GRADIENT_BOUND;
        long supportedUplift = Math.max(
                0L,
                (long) StrictMath.floor(
                        narrowAxis * calibration.maximumCardinalRiseSubunits() / gradientBound));
        long uplift = Math.min(requestedUplift, supportedUplift);

        double coreAlongOffset = centeredPpm(random, CORE, latticeX, latticeY, 0L)
                / (double) PPM
                * Math.min(negativeLongAxis, positiveLongAxis)
                * widthVariation
                * 0.45;
        double coreAcrossOffset = centeredPpm(random, CORE, latticeX, latticeY, 1L)
                / (double) PPM
                * Math.min(leftWidth, rightWidth)
                * widthVariation
                * 0.45;

        return new MountainSystem(
                centerX,
                centerY,
                axisX,
                axisY,
                negativeLongAxis,
                positiveLongAxis,
                leftWidth,
                rightWidth,
                coreAlongOffset,
                coreAcrossOffset,
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
        double base = layeredHill(radius, sharpness, false, recipe.plateauCorePpm());

        double coreAlong = along - system.coreAlongOffset();
        double coreAcross = across - system.coreAcrossOffset();
        double coreLongAxis = coreAlong < 0.0
                ? system.negativeLongAxis()
                : system.positiveLongAxis();
        double coreSideWidth = coreAcross < 0.0
                ? system.leftWidth()
                : system.rightWidth();
        double coreRadiusScale = recipe.coreRadiusPpm() / (double) PPM;
        double coreRadius = StrictMath.hypot(
                coreAlong / (coreLongAxis * coreRadiusScale),
                coreAcross / (coreSideWidth * coreRadiusScale));
        double core = coreRadius >= 1.0
                ? 0.0
                : layeredHill(
                        coreRadius,
                        Math.min(1.35, sharpness * 1.04),
                        system.plateau(),
                        recipe.plateauCorePpm());
        double coreWeight = recipe.coreWeightPpm() / (double) PPM;
        return Math.min(1.0, base + coreWeight * core * (1.0 - base));
    }

    /**
     * Layer-friendly radial profile: a long near-linear middle slope with smooth summit and foot.
     * Unlike smoothstep(1-r^2), it does not concentrate most vertical change into a narrow annulus.
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

    /** Cardinal distance from ocean/world edge, capped once the coastal fade is fully inland. */
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

    private static void applyCoastalFade(
            long[] uplift,
            boolean[] land,
            int[] coastalDistance,
            long maximumUplift,
            long shorelineUplift,
            int transitionCells) {
        if (maximumUplift <= 0L) return;
        double shorelineFactor = Math.max(
                0.08,
                Math.min(0.35, shorelineUplift / (double) maximumUplift));
        int transition = Math.max(2, transitionCells);
        for (int cell = 0; cell < uplift.length; cell++) {
            if (!land[cell] || uplift[cell] <= 0L) continue;
            double t = Math.max(
                    0.0,
                    Math.min(1.0, (coastalDistance[cell] - 1.0) / (transition - 1.0)));
            double smooth = t * t * (3.0 - 2.0 * t);
            double factor = shorelineFactor + (1.0 - shorelineFactor) * smooth;
            uplift[cell] = Math.max(0L, Math.round(uplift[cell] * factor));
        }
    }

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

    private static void removeInvisibleFringe(long[] uplift) {
        for (int cell = 0; cell < uplift.length; cell++) {
            if (uplift[cell] > 0L && uplift[cell] < MINIMUM_VISIBLE_UPLIFT_SUBUNITS) {
                uplift[cell] = 0L;
            }
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

    private record MountainSystem(
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double negativeLongAxis,
            double positiveLongAxis,
            double leftWidth,
            double rightWidth,
            double coreAlongOffset,
            double coreAcrossOffset,
            long upliftSubunits,
            boolean plateau) {
    }
}
