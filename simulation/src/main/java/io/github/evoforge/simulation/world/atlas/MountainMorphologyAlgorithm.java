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
 * <p>The accepted V12 hills are the visual reference: every mountain system is now one very large
 * anisotropic smooth hill rather than a narrow ridge wall. Chaininess stretches that hill along one
 * stable axis. A gentle coastal gate allows real coastal cliffs while preventing an offshore mask
 * boundary from slicing a tall mountain into a vertical wall. Overlapping systems still use a
 * maximum uplift field so intersections cannot create additive super-peaks.</p>
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
    private static final int UPLIFT_SMOOTHING_PASSES = 2;

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

        int[] coastalInteriority = coastalInteriorityPpm(
                land,
                width,
                height,
                recipe.coastalTransitionCells(),
                recipe.shorelineUpliftPpm());
        long[] maximumUplift = new long[calibration.area()];
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(
                random,
                bounds,
                land,
                width,
                height,
                calibration,
                recipe)) {
            rasterize(
                    system,
                    bounds,
                    width,
                    height,
                    land,
                    coastalInteriority,
                    maximumUplift,
                    calibration,
                    recipe);
        }
        smoothUplift(maximumUplift, land, width, height, UPLIFT_SMOOTHING_PASSES);

        long[] result = baseHeights.clone();
        long ceiling = calibration.mountainCeilingSubunits();
        for (int cell = 0; cell < result.length; cell++) {
            if (!land[cell] || maximumUplift[cell] <= 0L) continue;
            result[cell] = Math.min(ceiling, Math.addExact(result[cell], maximumUplift[cell]));
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
                MountainSystem system = createSystem(random, latticeX, latticeY, calibration, recipe);
                if (!centerIsLand(system, bounds, land, width, height)) continue;
                systems.add(system);
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
        double centerX = latticeX * (double) spacing + spacing * 0.5;
        double centerY = latticeY * (double) spacing + spacing * 0.5;
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

    private static boolean centerIsLand(
            MountainSystem system,
            WorldBounds bounds,
            boolean[] land,
            int width,
            int height) {
        int x = (int) StrictMath.round(system.centerX());
        int y = (int) StrictMath.round(system.centerY());
        if (x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY()) {
            return false;
        }
        int localX = x - bounds.minX();
        int localY = y - bounds.minY();
        return localX >= 0 && localX < width && localY >= 0 && localY < height
                && land[localY * width + localX];
    }

    private static void rasterize(
            MountainSystem system,
            WorldBounds bounds,
            int width,
            int height,
            boolean[] land,
            int[] coastalInteriority,
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
                uplift = uplift * coastalInteriority[cell] / PPM;
                if (uplift > maximumUplift[cell]) maximumUplift[cell] = uplift;
            }
        }
    }

    /** Same smooth radial law as a V12 hill, evaluated in an anisotropic ellipse. */
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

        if (system.plateau()) {
            double distance = StrictMath.sqrt(Math.max(0.0, distanceSquared));
            double plateauCore = recipe.plateauCorePpm() / (double) PPM;
            if (distance <= plateauCore) return 1.0;
            double remapped = (distance - plateauCore) / (1.0 - plateauCore);
            distanceSquared = remapped * remapped;
        }

        double coordinate = Math.max(0.0, Math.min(1.0, 1.0 - distanceSquared));
        double smooth = coordinate * coordinate * (3.0 - 2.0 * coordinate);
        double sharpness = calibration.sharpnessMilli() / 1_000.0;
        return StrictMath.pow(smooth, sharpness);
    }

    /**
     * Distance from ocean mapped to a smooth mountain-uplift gate. Shoreline cells retain a small
     * fraction of uplift, so coastal cliffs remain possible without producing full-height cut faces.
     */
    private static int[] coastalInteriorityPpm(
            boolean[] land,
            int width,
            int height,
            int transitionCells,
            int shorelineUpliftPpm) {
        int[] distance = new int[land.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (!land[cell]) {
                    distance[cell] = 0;
                } else if (x == 0 || x == width - 1 || y == 0 || y == height - 1) {
                    distance[cell] = 1;
                } else {
                    distance[cell] = transitionCells;
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
                distance[cell] = Math.min(transitionCells, best);
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (!land[cell]) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, distance[cell + 1] + 1);
                if (y + 1 < height) best = Math.min(best, distance[cell + width] + 1);
                distance[cell] = Math.min(transitionCells, best);
            }
        }

        int[] gate = new int[land.length];
        for (int cell = 0; cell < gate.length; cell++) {
            if (!land[cell]) {
                gate[cell] = 0;
                continue;
            }
            if (transitionCells <= 1 || distance[cell] >= transitionCells) {
                gate[cell] = PPM;
                continue;
            }
            int coordinate = (int) ((long) Math.max(0, distance[cell] - 1) * PPM
                    / (transitionCells - 1L));
            int smooth = smoothStepPpm(coordinate);
            gate[cell] = shorelineUpliftPpm
                    + (int) ((long) (PPM - shorelineUpliftPpm) * smooth / PPM);
        }
        return gate;
    }

    /** Two conservative low-pass passes remove max-composition seams without changing the base V12 field. */
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
                    if (x > 0 && land[cell - 1]) sum += uplift[cell - 1];
                    if (x + 1 < width && land[cell + 1]) sum += uplift[cell + 1];
                    if (y > 0 && land[cell - width]) sum += uplift[cell - width];
                    if (y + 1 < height && land[cell + width]) sum += uplift[cell + width];
                    scratch[cell] = sum / 8L;
                }
            }
            System.arraycopy(scratch, 0, uplift, 0, uplift.length);
        }
    }

    private static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) PPM, coordinatePpm));
        long squared = coordinate * coordinate;
        return (int) (squared * (3L * PPM - 2L * coordinate)
                / ((long) PPM * PPM));
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
