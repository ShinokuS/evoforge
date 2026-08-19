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
 * <p>Mountains are explicit ridge systems with rounded end caps, repeated peak/saddle hierarchy,
 * optional secondary branches, asymmetric side widths, broad foothills and per-system plateau
 * selection. Systems rasterize into a max-composed uplift field; they are never summed into
 * intersection spikes and they never change V12 ocean/land membership.</p>
 */
final class MountainMorphologyAlgorithm {
    private static final GenerationStageId STAGE_ID = GenerationStageId.of("world:mountains");
    private static final GenerationPurposeId ACTIVE = GenerationPurposeId.of("mountain:active");
    private static final GenerationPurposeId CENTER = GenerationPurposeId.of("mountain:center");
    private static final GenerationPurposeId ORIENTATION = GenerationPurposeId.of("mountain:orientation");
    private static final GenerationPurposeId WIDTH = GenerationPurposeId.of("mountain:width");
    private static final GenerationPurposeId HEIGHT = GenerationPurposeId.of("mountain:height");
    private static final GenerationPurposeId PLATEAU = GenerationPurposeId.of("mountain:plateau");
    private static final GenerationPurposeId PHASE = GenerationPurposeId.of("mountain:peak-phase");
    private static final GenerationPurposeId BRANCH = GenerationPurposeId.of("mountain:branch");
    private static final int PPM = NormalizedValue.SCALE;
    private static final double TWO_PI = StrictMath.PI * 2.0;

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

        long[] maximumUplift = new long[calibration.area()];
        GenerationRandom random = GenerationRandom.from(genesis);
        for (MountainSystem system : createSystems(random, bounds, calibration, recipe)) {
            rasterize(system, bounds, width, height, land, maximumUplift, calibration, recipe);
        }

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
            MountainCalibration calibration,
            MountainRecipe recipe) {
        int spacing = calibration.candidateSpacingCells();
        int maximumWidth = Math.max(
                1,
                (int) StrictMath.ceil(calibration.typicalHalfWidthCells()
                        * (1.0 + recipe.widthVariationPpm() / (double) PPM)));
        int branchLength = Math.max(
                1,
                (int) ((long) maximumWidth * recipe.branchLengthWidthPpm() / PPM));
        int foothillWidth = Math.max(
                maximumWidth,
                (int) ((long) maximumWidth * recipe.foothillWidthPpm() / PPM));
        int support = calibration.ridgeHalfLengthCells() + branchLength + foothillWidth;
        int margin = Math.max(1, (support + spacing - 1) / spacing + 1);

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
        double centerX = latticeX * (double) spacing + spacing * 0.5;
        double centerY = latticeY * (double) spacing + spacing * 0.5;
        double maximumJitter = spacing * recipe.centerJitterPpm() / (double) PPM;
        centerX += centeredPpm(random, CENTER, latticeX, latticeY, 0L) * maximumJitter / PPM;
        centerY += centeredPpm(random, CENTER, latticeX, latticeY, 1L) * maximumJitter / PPM;

        double angle = samplePpm(random, ORIENTATION, latticeX, latticeY, 0L) * TWO_PI / PPM;
        double axisX = StrictMath.cos(angle);
        double axisY = StrictMath.sin(angle);

        double baseWidth = calibration.typicalHalfWidthCells();
        double widthVariation = recipe.widthVariationPpm() / (double) PPM;
        double leftWidth = variedPositive(
                baseWidth,
                centeredPpm(random, WIDTH, latticeX, latticeY, 0L),
                widthVariation);
        double rightWidth = variedPositive(
                baseWidth,
                centeredPpm(random, WIDTH, latticeX, latticeY, 1L),
                widthVariation);

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
        double phase = samplePpm(random, PHASE, latticeX, latticeY, 0L) * TWO_PI / PPM;

        List<Branch> branches = new ArrayList<>(recipe.maximumBranches());
        for (int branchIndex = 0; branchIndex < recipe.maximumBranches(); branchIndex++) {
            if (samplePpm(random, BRANCH, latticeX, latticeY, branchIndex * 8L)
                    >= calibration.branchProbabilityPpm()) {
                continue;
            }
            double anchorCoordinate = centeredPpm(
                    random, BRANCH, latticeX, latticeY, branchIndex * 8L + 1L) / (double) PPM;
            double anchorAlong = anchorCoordinate * calibration.ridgeHalfLengthCells() * 0.72;
            double anchorX = centerX + axisX * anchorAlong;
            double anchorY = centerY + axisY * anchorAlong;

            double turnCoordinate = samplePpm(
                    random, BRANCH, latticeX, latticeY, branchIndex * 8L + 2L) / (double) PPM;
            double turnDegrees = 35.0 + turnCoordinate * 35.0;
            double turnSign = (branchIndex & 1) == 0 ? 1.0 : -1.0;
            double branchAngle = angle + turnSign * turnDegrees * StrictMath.PI / 180.0;
            double branchAxisX = StrictMath.cos(branchAngle);
            double branchAxisY = StrictMath.sin(branchAngle);
            double branchHalfLength = Math.max(
                    1.0,
                    baseWidth * recipe.branchLengthWidthPpm() / PPM);
            double branchWidth = Math.max(1.0, baseWidth * 0.68);
            double branchPhase = samplePpm(
                    random, BRANCH, latticeX, latticeY, branchIndex * 8L + 3L) * TWO_PI / PPM;
            branches.add(new Branch(
                    anchorX,
                    anchorY,
                    branchAxisX,
                    branchAxisY,
                    branchHalfLength,
                    branchWidth,
                    branchPhase));
        }

        return new MountainSystem(
                centerX,
                centerY,
                axisX,
                axisY,
                calibration.ridgeHalfLengthCells(),
                leftWidth,
                rightWidth,
                uplift,
                phase,
                plateau,
                branches.toArray(Branch[]::new));
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
        double sideWidth = Math.max(system.leftWidth(), system.rightWidth());
        double foothillWidth = sideWidth * recipe.foothillWidthPpm() / PPM;
        double branchReach = sideWidth * recipe.branchLengthWidthPpm() / PPM;
        double support = system.halfLength() + foothillWidth + branchReach;
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

                double profile = mainProfile(system, x, y, calibration, recipe);
                for (Branch branch : system.branches()) {
                    profile = Math.max(profile, 0.76 * branchProfile(branch, x, y, calibration, recipe));
                }
                if (profile <= 0.0) continue;
                long uplift = Math.max(0L, Math.round(system.upliftSubunits() * profile));
                if (uplift > maximumUplift[cell]) maximumUplift[cell] = uplift;
            }
        }
    }

    private static double mainProfile(
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
        return ridgeProfile(
                along,
                across,
                system.halfLength(),
                sideWidth,
                system.phase(),
                system.plateau(),
                calibration,
                recipe);
    }

    private static double branchProfile(
            Branch branch,
            double x,
            double y,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        double dx = x - branch.anchorX();
        double dy = y - branch.anchorY();
        double along = dx * branch.axisX() + dy * branch.axisY();
        double across = -dx * branch.axisY() + dy * branch.axisX();
        return ridgeProfile(
                along,
                across,
                branch.halfLength(),
                branch.halfWidth(),
                branch.phase(),
                false,
                calibration,
                recipe);
    }

    private static double ridgeProfile(
            double along,
            double across,
            double halfLength,
            double halfWidth,
            double phase,
            boolean plateau,
            MountainCalibration calibration,
            MountainRecipe recipe) {
        if (halfWidth <= 0.0) return 0.0;
        double beyondEnd = Math.max(0.0, StrictMath.abs(along) - halfLength);
        double normalized = StrictMath.hypot(across / halfWidth, beyondEnd / halfWidth);
        double sharpness = calibration.sharpnessMilli() / 1_000.0;
        double core = profile(normalized, sharpness, plateau, recipe.plateauCorePpm());

        double foothillWidth = halfWidth * recipe.foothillWidthPpm() / PPM;
        double foothillNormalized = StrictMath.hypot(
                across / foothillWidth,
                beyondEnd / foothillWidth);
        double foothill = profile(foothillNormalized, 1.15, false, 0)
                * recipe.foothillWeightPpm() / PPM;

        if (core <= 0.0) return foothill;
        double peakWave = 0.5 + 0.5 * StrictMath.cos(
                along / calibration.peakSpacingCells() * TWO_PI + phase);
        double saddleFloor = recipe.saddleFloorPpm() / (double) PPM;
        double peakFactor = saddleFloor + (1.0 - saddleFloor) * peakWave;
        if (plateau && peakFactor >= 0.88) peakFactor = 1.0;
        return Math.max(core * peakFactor, foothill);
    }

    private static double profile(
            double normalizedDistance,
            double sharpness,
            boolean plateau,
            int plateauCorePpm) {
        if (normalizedDistance >= 1.0) return 0.0;
        double distance = Math.max(0.0, normalizedDistance);
        if (plateau) {
            double core = plateauCorePpm / (double) PPM;
            if (distance <= core) distance = 0.0;
            else distance = (distance - core) / (1.0 - core);
        }
        double t = Math.max(0.0, Math.min(1.0, 1.0 - distance));
        double smooth = t * t * (3.0 - 2.0 * t);
        return StrictMath.pow(smooth, sharpness);
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
            double halfLength,
            double leftWidth,
            double rightWidth,
            long upliftSubunits,
            double phase,
            boolean plateau,
            Branch[] branches) {
    }

    private record Branch(
            double anchorX,
            double anchorY,
            double axisX,
            double axisY,
            double halfLength,
            double halfWidth,
            double phase) {
    }
}
