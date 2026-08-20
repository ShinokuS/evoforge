package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * V14 continent/island silhouette grown from separated seeds on a jittered coarse scaffold.
 *
 * <p>Unlike random LAND/OCEAN site classification, every terrestrial region begins at an explicit
 * seed and expands as a compact multi-source front. Farthest-point seed placement gives fragmented
 * worlds genuinely separate geographic nuclei. Bounded growth-rate, directional and traversal
 * variation keep fronts organic without allowing random low-cost tendrils to own macro shape.
 * Coast warp and relaxation remain secondary raster detail after the compact topology exists.</p>
 */
final class CompactRegionLandmassSilhouetteAlgorithm implements LandmassSilhouetteAlgorithm {
    static final CompactRegionLandmassSilhouetteAlgorithm INSTANCE =
            new CompactRegionLandmassSilhouetteAlgorithm();

    private static final GenerationPurposeId SITE_JITTER =
            GenerationPurposeId.of("world:v14-compact-site-jitter");
    private static final GenerationPurposeId FIRST_SEED =
            GenerationPurposeId.of("world:v14-compact-first-seed");
    private static final GenerationPurposeId SEED_JITTER =
            GenerationPurposeId.of("world:v14-compact-seed-jitter");
    private static final GenerationPurposeId GROWTH_RATE =
            GenerationPurposeId.of("world:v14-compact-growth-rate");
    private static final GenerationPurposeId GROWTH_DIRECTION =
            GenerationPurposeId.of("world:v14-compact-growth-direction");
    private static final GenerationPurposeId GROWTH_NOISE =
            GenerationPurposeId.of("world:v14-compact-growth-noise");
    private static final GenerationPurposeId WARP_X =
            GenerationPurposeId.of("world:v14-compact-coast-warp-x");
    private static final GenerationPurposeId WARP_Y =
            GenerationPurposeId.of("world:v14-compact-coast-warp-y");
    private static final GenerationPurposeId COAST_DETAIL =
            GenerationPurposeId.of("world:v14-compact-coast-detail");

    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;
    private static final double TWO_PI = StrictMath.PI * 2d;

    private CompactRegionLandmassSilhouetteAlgorithm() {
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
        CoarseGrid grid = createScaffold(random, width, height, boundary, calibration, recipe);
        boolean[] landSite = growCompactLandRegions(
                random,
                grid,
                boundary.maximumLandCells(),
                area,
                calibration,
                recipe.growth());
        BoundaryOceanAnchors boundaryOcean = createBoundaryOceanAnchors(
                width,
                height,
                grid.spacingCells());
        CoastField coast = materializeCoastField(
                random,
                grid,
                landSite,
                boundaryOcean,
                width,
                height,
                boundary,
                recipe);
        CoastField relaxedCoast = new CoastField(CoastFieldRelaxationAlgorithm.standard().relax(
                coast.score(),
                width,
                height,
                boundary.minimumOceanMarginCells(),
                grid.spacingCells(),
                recipe.relaxation()));
        return materializeSilhouette(
                bounds,
                relaxedCoast,
                boundary.maximumLandCells(),
                calibration.silhouetteInfluencePpm());
    }

    private static CoarseGrid createScaffold(
            GenerationRandom random,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe recipe) {
        int spacing = calibration.scaffoldSpacingCells();
        int columns = Math.max(3, Math.floorDiv(width + spacing - 1, spacing) + 4);
        int rows = Math.max(3, Math.floorDiv(height + spacing - 1, spacing) + 4);
        int count = Math.multiplyExact(columns, rows);
        double[] x = new double[count];
        double[] y = new double[count];
        boolean[] forcedOcean = new boolean[count];
        LandmassSilhouetteRecipe.ScaffoldPolicy scaffold = recipe.scaffold();
        double jitter = spacing * scaffold.siteJitterPpm() / (double) PPM;
        double oceanBand = boundary.minimumOceanMarginCells()
                + spacing * scaffold.oceanSeedBandSpacingPpm() / (double) PPM;

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
                index++;
            }
        }
        return new CoarseGrid(columns, rows, spacing, x, y, forcedOcean);
    }

    private static boolean[] growCompactLandRegions(
            GenerationRandom random,
            CoarseGrid grid,
            int maximumLandCells,
            int worldArea,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe.GrowthPolicy policy) {
        int candidates = 0;
        for (boolean forcedOcean : grid.forcedOcean()) {
            if (!forcedOcean) candidates++;
        }
        boolean[] land = new boolean[grid.x().length];
        if (candidates == 0 || maximumLandCells <= 0) return land;

        double fragmentation = calibration.fragmentationPpm() / (double) PPM;
        double expansion = lerp(
                policy.cohesiveSupportExpansionPpm() / (double) PPM,
                policy.fragmentedSupportExpansionPpm() / (double) PPM,
                fragmentation);
        double maximumSupport = lerp(
                policy.cohesiveMaximumSupportPpm() / (double) PPM,
                policy.fragmentedMaximumSupportPpm() / (double) PPM,
                fragmentation);
        double requestedWorldFraction = maximumLandCells / (double) worldArea;
        double desiredFraction = Math.min(maximumSupport, requestedWorldFraction * expansion);
        int desiredSites = Math.max(1, Math.min(candidates,
                (int) StrictMath.ceil(candidates * desiredFraction)));
        int clusterCount = Math.min(
                Math.min(calibration.landClusterCount(), desiredSites),
                candidates);

        int[] seeds = chooseSeparatedSeeds(random, grid, clusterCount);
        GrowthField growth = resolveGrowthField(random, grid, seeds, policy);
        RankedSite[] ranks = new RankedSite[candidates];
        int rankIndex = 0;
        for (int site = 0; site < grid.x().length; site++) {
            if (grid.forcedOcean()[site]) continue;
            ranks[rankIndex++] = new RankedSite(site, growth.cost()[site], growth.owner()[site]);
        }
        Arrays.sort(ranks, Comparator
                .comparingDouble(RankedSite::cost)
                .thenComparingInt(RankedSite::owner)
                .thenComparingInt(RankedSite::site));
        for (int rank = 0; rank < desiredSites; rank++) {
            if (!Double.isFinite(ranks[rank].cost())) {
                throw new IllegalStateException("compact land growth left an interior scaffold site unreachable");
            }
            land[ranks[rank].site()] = true;
        }
        return land;
    }

    private static int[] chooseSeparatedSeeds(
            GenerationRandom random,
            CoarseGrid grid,
            int clusterCount) {
        int[] candidates = new int[grid.x().length];
        int candidateCount = 0;
        for (int site = 0; site < grid.x().length; site++) {
            if (!grid.forcedOcean()[site]) candidates[candidateCount++] = site;
        }
        if (candidateCount == 0) throw new IllegalStateException("landmass scaffold has no interior seed sites");

        int[] seeds = new int[clusterCount];
        int first = candidates[0];
        int bestRank = -1;
        for (int i = 0; i < candidateCount; i++) {
            int site = candidates[i];
            int rank = randomPpm(random, FIRST_SEED, site, 0L, 0L);
            if (rank > bestRank || rank == bestRank && site < first) {
                bestRank = rank;
                first = site;
            }
        }
        seeds[0] = first;

        for (int seedIndex = 1; seedIndex < clusterCount; seedIndex++) {
            int selected = -1;
            double selectedScore = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < candidateCount; i++) {
                int site = candidates[i];
                if (contains(seeds, seedIndex, site)) continue;
                double minimumDistanceSquared = Double.POSITIVE_INFINITY;
                for (int existing = 0; existing < seedIndex; existing++) {
                    int seed = seeds[existing];
                    double dx = grid.x()[site] - grid.x()[seed];
                    double dy = grid.y()[site] - grid.y()[seed];
                    minimumDistanceSquared = Math.min(minimumDistanceSquared, dx * dx + dy * dy);
                }
                double jitter = 0.90d + 0.20d
                        * randomPpm(random, SEED_JITTER, site, seedIndex, 0L) / PPM;
                double score = minimumDistanceSquared * jitter;
                if (score > selectedScore || score == selectedScore && site < selected) {
                    selectedScore = score;
                    selected = site;
                }
            }
            if (selected < 0) throw new IllegalStateException("unable to place separated landmass seed");
            seeds[seedIndex] = selected;
        }
        return seeds;
    }

    private static GrowthField resolveGrowthField(
            GenerationRandom random,
            CoarseGrid grid,
            int[] seeds,
            LandmassSilhouetteRecipe.GrowthPolicy policy) {
        int count = grid.x().length;
        double[] cost = new double[count];
        Arrays.fill(cost, Double.POSITIVE_INFINITY);
        int[] owner = new int[count];
        Arrays.fill(owner, -1);
        for (int site = 0; site < count; site++) {
            if (grid.forcedOcean()[site]) owner[site] = -2;
        }

        double[] growthRate = new double[seeds.length];
        double[] axisX = new double[seeds.length];
        double[] axisY = new double[seeds.length];
        double variation = policy.growthRateVariationPpm() / (double) PPM;
        for (int cluster = 0; cluster < seeds.length; cluster++) {
            growthRate[cluster] = 1d + centeredUnit(random, GROWTH_RATE, cluster, 0L, 0L) * variation;
            double angle = randomPpm(random, GROWTH_DIRECTION, cluster, 0L, 0L)
                    / (double) PPM * TWO_PI;
            axisX[cluster] = StrictMath.cos(angle);
            axisY[cluster] = StrictMath.sin(angle);
        }

        PriorityQueue<Frontier> frontier = new PriorityQueue<>(Comparator
                .comparingDouble(Frontier::cost)
                .thenComparingInt(Frontier::cluster)
                .thenComparingInt(Frontier::site));
        for (int cluster = 0; cluster < seeds.length; cluster++) {
            int seed = seeds[cluster];
            cost[seed] = 0d;
            owner[seed] = cluster;
            frontier.add(new Frontier(seed, cluster, 0d));
        }

        double directionalBias = policy.directionalBiasPpm() / (double) PPM;
        double traversalNoise = policy.traversalNoisePpm() / (double) PPM;
        while (!frontier.isEmpty()) {
            Frontier current = frontier.remove();
            if (current.cost() != cost[current.site()] || current.cluster() != owner[current.site()]) continue;
            int gx = current.site() % grid.columns();
            int gy = current.site() / grid.columns();
            for (int oy = -1; oy <= 1; oy++) {
                for (int ox = -1; ox <= 1; ox++) {
                    if (ox == 0 && oy == 0) continue;
                    int nx = gx + ox;
                    int ny = gy + oy;
                    if (nx < 0 || nx >= grid.columns() || ny < 0 || ny >= grid.rows()) continue;
                    int next = ny * grid.columns() + nx;
                    if (grid.forcedOcean()[next]) continue;

                    int cluster = current.cluster();
                    double dx = grid.x()[next] - grid.x()[current.site()];
                    double dy = grid.y()[next] - grid.y()[current.site()];
                    double edgeLength = StrictMath.hypot(dx, dy);
                    if (!(edgeLength > 0d)) continue;
                    double alignment = StrictMath.abs((dx * axisX[cluster] + dy * axisY[cluster]) / edgeLength);
                    double directionFactor = 1d + directionalBias * (1d - 2d * alignment);
                    double noise = centeredUnit(random, GROWTH_NOISE, nx - 2L, ny - 2L, cluster);
                    double noiseFactor = 1d + noise * traversalNoise;
                    double edgeCost = edgeLength * directionFactor * noiseFactor / growthRate[cluster];
                    double candidateCost = current.cost() + edgeCost;
                    if (candidateCost < cost[next] - 1.0e-9d
                            || StrictMath.abs(candidateCost - cost[next]) <= 1.0e-9d && cluster < owner[next]) {
                        cost[next] = candidateCost;
                        owner[next] = cluster;
                        frontier.add(new Frontier(next, cluster, candidateCost));
                    }
                }
            }
        }
        return new GrowthField(cost, owner);
    }

    /**
     * Samples the finite-world frame with ocean sites dense enough that every boundary raster cell
     * competes against explicit external ocean in the same distance field as terrestrial regions.
     */
    private static BoundaryOceanAnchors createBoundaryOceanAnchors(
            int width,
            int height,
            int scaffoldSpacing) {
        int targetSpacing = Math.max(1, scaffoldSpacing / 2);
        int horizontalSegments = Math.max(1, Math.floorDiv(width - 1 + targetSpacing - 1, targetSpacing));
        int verticalSegments = Math.max(1, Math.floorDiv(height - 1 + targetSpacing - 1, targetSpacing));
        int count = 2 * (horizontalSegments + 1)
                + 2 * Math.max(0, verticalSegments - 1);
        double[] x = new double[count];
        double[] y = new double[count];
        int index = 0;

        for (int segment = 0; segment <= horizontalSegments; segment++) {
            double coordinate = (width - 1d) * segment / horizontalSegments;
            x[index] = coordinate;
            y[index++] = 0d;
            x[index] = coordinate;
            y[index++] = height - 1d;
        }
        for (int segment = 1; segment < verticalSegments; segment++) {
            double coordinate = (height - 1d) * segment / verticalSegments;
            x[index] = 0d;
            y[index++] = coordinate;
            x[index] = width - 1d;
            y[index++] = coordinate;
        }
        if (index != count) throw new IllegalStateException("boundary ocean anchor count mismatch");
        return new BoundaryOceanAnchors(x, y);
    }

    private static CoastField materializeCoastField(
            GenerationRandom random,
            CoarseGrid grid,
            boolean[] landSite,
            BoundaryOceanAnchors boundaryOcean,
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
                    if (landSite[site]) nearestLand = Math.min(nearestLand, distanceSquared);
                    else nearestOcean = Math.min(nearestOcean, distanceSquared);
                }
                for (int anchor = 0; anchor < boundaryOcean.x().length; anchor++) {
                    double dx = px - boundaryOcean.x()[anchor];
                    double dy = py - boundaryOcean.y()[anchor];
                    nearestOcean = Math.min(nearestOcean, dx * dx + dy * dy);
                }
                if (!Double.isFinite(nearestLand)) {
                    score[index++] = -Double.MAX_VALUE;
                    continue;
                }
                double regionMargin = StrictMath.sqrt(nearestOcean) - StrictMath.sqrt(nearestLand);
                double detail = smoothNoise(random, COAST_DETAIL, localX, localY, detailScale)
                        * detailAmplitude * deformationFactor;
                double coastScore = regionMargin + detail;
                if (edgeDistance < guaranteedMargin && coastScore >= 0d) {
                    throw new IllegalStateException(
                            "compact land growth breached guaranteed external-ocean boundary");
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
            throw new IllegalStateException("compact region growth produced no terrestrial support");
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
            throw new IllegalStateException("compact region support violated finite-world land capacity");
        }
        double denominator = Math.max(0.000_001d, maximumInterior);
        for (int i = 0; i < score.length; i++) {
            if (!support[i]) continue;
            long normalized = StrictMath.round((score[i] - cutoff) / denominator * PPM);
            potentialPpm[i] = (int) Math.max(1L, Math.min((long) PPM, normalized));
        }
        return new LandmassSilhouette(bounds, support, potentialPpm, supportCount, influencePpm);
    }

    private static boolean contains(int[] values, int length, int target) {
        for (int index = 0; index < length; index++) {
            if (values[index] == target) return true;
        }
        return false;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
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

    private record CoarseGrid(
            int columns,
            int rows,
            int spacingCells,
            double[] x,
            double[] y,
            boolean[] forcedOcean) {
    }

    private record GrowthField(double[] cost, int[] owner) {
    }

    private record Frontier(int site, int cluster, double cost) {
    }

    private record RankedSite(int site, double cost, int owner) {
    }

    private record BoundaryOceanAnchors(double[] x, double[] y) {
    }

    private record CoastField(double[] score) {
    }
}
