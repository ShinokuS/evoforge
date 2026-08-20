package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;

/**
 * V14 land/water topology synthesized from a jittered plate-control scaffold.
 *
 * <p>The scaffold deliberately separates large-scale topology from coastline detail. Jittered
 * control sites form Voronoi-like plates. A correlated scalar on those sites determines which
 * plates belong to terrestrial clusters; correlation length is calibrated from continent scale and
 * fragmentation. World-edge sites are oceanic control sites, so finite-world ocean clearance is a
 * property of the scaffold rather than a rectangular clipping mask. The final coastline is the
 * equal-distance field between land and ocean sites after smooth coordinate warping and secondary
 * coast perturbation. Coast deformation fades out before the guaranteed external-ocean clearance;
 * breaching that clearance is a generation error rather than an opportunity to clip the coast.</p>
 */
final class PlateLandmassSilhouetteAlgorithm implements LandmassSilhouetteAlgorithm {
    static final PlateLandmassSilhouetteAlgorithm INSTANCE = new PlateLandmassSilhouetteAlgorithm();

    private static final GenerationPurposeId SITE_JITTER = GenerationPurposeId.of("world:v14-plate-site-jitter");
    private static final GenerationPurposeId SITE_STATE = GenerationPurposeId.of("world:v14-plate-state");
    private static final GenerationPurposeId WARP_X = GenerationPurposeId.of("world:v14-coast-warp-x");
    private static final GenerationPurposeId WARP_Y = GenerationPurposeId.of("world:v14-coast-warp-y");
    private static final GenerationPurposeId COAST_DETAIL = GenerationPurposeId.of("world:v14-coast-detail");
    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;

    private PlateLandmassSilhouetteAlgorithm() {
    }

    @Override
    public LandmassSilhouette generate(
            WorldGenesis genesis,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe recipe) {
        if (genesis == null || boundary == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("landmass silhouette inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = DenseElevationField.cellCount(bounds);
        if (area == 0 || boundary.maximumLandCells() == 0) {
            return new LandmassSilhouette(
                    bounds,
                    new boolean[area],
                    new int[area],
                    0,
                    calibration.silhouetteInfluencePpm());
        }

        GenerationRandom random = GenerationRandom.from(genesis);
        PlateGrid grid = createPlateGrid(
                random,
                width,
                height,
                boundary,
                calibration,
                recipe);
        boolean[] landSite = classifyLandSites(grid, boundary.maximumLandCells(), area);
        CoastField coast = materializeCoastField(
                random,
                grid,
                landSite,
                width,
                height,
                boundary,
                recipe);
        return materializeSilhouette(
                bounds,
                coast,
                boundary.maximumLandCells(),
                calibration.silhouetteInfluencePpm());
    }

    private static PlateGrid createPlateGrid(
            GenerationRandom random,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe recipe) {
        int spacing = calibration.plateSpacingCells();
        int columns = Math.max(3, Math.floorDiv(width + spacing - 1, spacing) + 4);
        int rows = Math.max(3, Math.floorDiv(height + spacing - 1, spacing) + 4);
        int count = Math.multiplyExact(columns, rows);
        double[] x = new double[count];
        double[] y = new double[count];
        boolean[] forcedOcean = new boolean[count];
        int[] state = new int[count];
        LandmassSilhouetteRecipe.PlatePolicy plates = recipe.plates();
        double jitter = spacing * plates.siteJitterPpm() / (double) PPM;
        double oceanBand = boundary.minimumOceanMarginCells()
                + spacing * plates.oceanSeedBandSpacingPpm() / (double) PPM;

        int index = 0;
        for (int gy = 0; gy < rows; gy++) {
            int latticeY = gy - 2;
            double baseY = (latticeY + 0.5d) * spacing;
            for (int gx = 0; gx < columns; gx++) {
                int latticeX = gx - 2;
                double baseX = (latticeX + 0.5d) * spacing;
                double jitterX = centeredUnit(random, SITE_JITTER, latticeX, latticeY, 0L) * jitter;
                double jitterY = centeredUnit(random, SITE_JITTER, latticeX, latticeY, 1L) * jitter;
                x[index] = baseX + jitterX;
                y[index] = baseY + jitterY;
                forcedOcean[index] = x[index] < oceanBand
                        || y[index] < oceanBand
                        || x[index] > width - 1d - oceanBand
                        || y[index] > height - 1d - oceanBand;
                state[index] = forcedOcean[index]
                        ? 0
                        : randomPpm(random, SITE_STATE, latticeX, latticeY, 0L);
                index++;
            }
        }

        int[] current = state;
        for (int pass = 0; pass < calibration.correlationPasses(); pass++) {
            int[] next = Arrays.copyOf(current, current.length);
            for (int gy = 1; gy < rows - 1; gy++) {
                for (int gx = 1; gx < columns - 1; gx++) {
                    int cell = gy * columns + gx;
                    if (forcedOcean[cell]) {
                        next[cell] = 0;
                        continue;
                    }
                    long sum = (long) current[cell] * 4L;
                    int weight = 4;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            sum += current[(gy + oy) * columns + gx + ox];
                            weight++;
                        }
                    }
                    next[cell] = Math.toIntExact(sum / weight);
                }
            }
            current = next;
        }
        for (int i = 0; i < current.length; i++) {
            if (forcedOcean[i]) current[i] = 0;
        }
        return new PlateGrid(columns, rows, spacing, calibration.fragmentationPpm(), x, y, forcedOcean, current);
    }

    private static boolean[] classifyLandSites(
            PlateGrid grid,
            int maximumLandCells,
            int worldArea) {
        int candidates = 0;
        for (boolean forcedOcean : grid.forcedOcean()) {
            if (!forcedOcean) candidates++;
        }
        boolean[] land = new boolean[grid.statePpm().length];
        if (candidates == 0 || maximumLandCells <= 0) return land;

        long[] ranks = new long[candidates];
        int rankIndex = 0;
        for (int site = 0; site < grid.statePpm().length; site++) {
            if (grid.forcedOcean()[site]) continue;
            long inverted = (long) PPM - grid.statePpm()[site];
            ranks[rankIndex++] = (inverted << 32) | (site & 0xffff_ffffL);
        }
        Arrays.sort(ranks);

        double maximumWorldFraction = maximumLandCells / (double) worldArea;
        double fragmentation = grid.fragmentationPpm() / (double) PPM;
        double topologyExpansion = 1.60d - fragmentation * 0.25d;
        double desiredFraction = Math.min(0.82d, maximumWorldFraction * topologyExpansion);
        int desired = Math.min(candidates, Math.max(1,
                (int) StrictMath.ceil(candidates * desiredFraction)));
        for (int rank = 0; rank < desired; rank++) {
            land[(int) ranks[rank]] = true;
        }
        return land;
    }

    private static CoastField materializeCoastField(
            GenerationRandom random,
            PlateGrid grid,
            boolean[] landSite,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteRecipe recipe) {
        int area = Math.multiplyExact(width, height);
        double[] score = new double[area];
        LandmassSilhouetteRecipe.CoastPolicy coast = recipe.coast();
        double warpScale = Math.max(2d,
                grid.spacingCells() * coast.warpScaleSpacingPpm() / (double) PPM);
        double detailScale = Math.max(2d,
                grid.spacingCells() * coast.detailScaleSpacingPpm() / (double) PPM);
        double warpAmplitude = grid.spacingCells()
                * coast.warpAmplitudeSpacingPpm() / (double) PPM;
        double detailAmplitude = grid.spacingCells()
                * coast.detailAmplitudeSpacingPpm() / (double) PPM;
        int guaranteedMargin = boundary.minimumOceanMarginCells();
        double deformationRampCells = Math.max(1d, grid.spacingCells());

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                int edgeDistance = edgeDistance(localX, localY, width, height);
                double deformationCoordinate = (edgeDistance - guaranteedMargin) / deformationRampCells;
                double deformationFactor = smooth(clamp01(deformationCoordinate));
                double warpX = smoothNoise(random, WARP_X, localX, localY, warpScale)
                        * warpAmplitude * deformationFactor;
                double warpY = smoothNoise(random, WARP_Y, localX, localY, warpScale)
                        * warpAmplitude * deformationFactor;
                double px = localX + warpX;
                double py = localY + warpY;
                double nearestLand = Double.POSITIVE_INFINITY;
                double nearestOcean = Double.POSITIVE_INFINITY;
                for (int site = 0; site < landSite.length; site++) {
                    double dx = px - grid.x()[site];
                    double dy = py - grid.y()[site];
                    double distanceSquared = dx * dx + dy * dy;
                    if (landSite[site]) {
                        nearestLand = Math.min(nearestLand, distanceSquared);
                    } else {
                        nearestOcean = Math.min(nearestOcean, distanceSquared);
                    }
                }
                if (!Double.isFinite(nearestLand)) {
                    score[index++] = -Double.MAX_VALUE;
                    continue;
                }
                double plateMargin = StrictMath.sqrt(nearestOcean) - StrictMath.sqrt(nearestLand);
                double detail = smoothNoise(random, COAST_DETAIL, localX, localY, detailScale)
                        * detailAmplitude * deformationFactor;
                double coastScore = plateMargin + detail;
                if (edgeDistance < guaranteedMargin && coastScore >= 0d) {
                    throw new IllegalStateException(
                            "plate scaffold breached guaranteed ocean clearance instead of ending naturally");
                }
                score[index++] = coastScore;
            }
        }
        return new CoastField(score);
    }

    private static LandmassSilhouette materializeSilhouette(
            WorldBounds bounds,
            CoastField coast,
            int maximumLandCells,
            int influencePpm) {
        double[] score = coast.score();
        int positiveCount = 0;
        for (double value : score) {
            if (value > 0d) positiveCount++;
        }
        if (positiveCount == 0) {
            throw new IllegalStateException("plate scaffold produced no terrestrial support");
        }

        double cutoff = 0d;
        if (positiveCount > maximumLandCells) {
            double[] positive = new double[positiveCount];
            int i = 0;
            for (double value : score) {
                if (value > 0d) positive[i++] = value;
            }
            Arrays.sort(positive);
            cutoff = positive[positiveCount - maximumLandCells];
        }

        boolean[] support = new boolean[score.length];
        int[] potentialPpm = new int[score.length];
        int supportCount = 0;
        double maximumInterior = 0d;
        for (int i = 0; i < score.length; i++) {
            if (!(score[i] > cutoff)) continue;
            support[i] = true;
            supportCount++;
            maximumInterior = Math.max(maximumInterior, score[i] - cutoff);
        }
        if (supportCount == 0 || supportCount > maximumLandCells) {
            throw new IllegalStateException("plate scaffold land support violated finite-world capacity");
        }
        double denominator = Math.max(0.000_001d, maximumInterior);
        for (int i = 0; i < score.length; i++) {
            if (!support[i]) continue;
            long normalized = StrictMath.round((score[i] - cutoff) / denominator * PPM);
            potentialPpm[i] = (int) Math.max(1L, Math.min((long) PPM, normalized));
        }
        return new LandmassSilhouette(bounds, support, potentialPpm, supportCount, influencePpm);
    }

    private static double smoothNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            double x,
            double y,
            double scale) {
        double gridX = x / scale;
        double gridY = y / scale;
        long x0 = (long) StrictMath.floor(gridX);
        long y0 = (long) StrictMath.floor(gridY);
        double tx = smooth(gridX - x0);
        double ty = smooth(gridY - y0);
        double a = centeredUnit(random, purpose, x0, y0, 0L);
        double b = centeredUnit(random, purpose, x0 + 1L, y0, 0L);
        double c = centeredUnit(random, purpose, x0, y0 + 1L, 0L);
        double d = centeredUnit(random, purpose, x0 + 1L, y0 + 1L, 0L);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }

    private static double centeredUnit(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, purpose, x, y, ordinal) / (double) PPM * 2d - 1d;
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
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    private record PlateGrid(
            int columns,
            int rows,
            int spacingCells,
            int fragmentationPpm,
            double[] x,
            double[] y,
            boolean[] forcedOcean,
            int[] statePpm) {
    }

    private record CoastField(double[] score) {
    }
}
