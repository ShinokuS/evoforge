package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.Arrays;
import java.util.Comparator;

/**
 * V14 continent/island geometry synthesized as a regularized land phase on an irregularized graph.
 *
 * <p>The algorithm deliberately separates macro geography from coastline detail. Broad separated
 * nuclei and a low-frequency geographic forcing field propose where land belongs. A
 * volume-preserving threshold-dynamics pass then repeatedly diffuses and re-thresholds that phase,
 * making excessive perimeter, one-cell tendrils and sharp coarse-grid corners expensive by
 * construction. The accepted graph phase is finally interpolated into a smooth compact-support
 * implicit coast field; weak broad warp and bounded raster relaxation may deform that field, but
 * neither owns macro topology.</p>
 */
final class RegularizedGraphLandmassSilhouetteAlgorithm implements LandmassSilhouetteAlgorithm {
    static final RegularizedGraphLandmassSilhouetteAlgorithm INSTANCE =
            new RegularizedGraphLandmassSilhouetteAlgorithm();

    private static final GenerationPurposeId SITE_JITTER =
            GenerationPurposeId.of("world:v14-phase-site-jitter");
    private static final GenerationPurposeId FIRST_SEED =
            GenerationPurposeId.of("world:v14-phase-first-seed");
    private static final GenerationPurposeId SEED_JITTER =
            GenerationPurposeId.of("world:v14-phase-seed-jitter");
    private static final GenerationPurposeId CLUSTER_AXIS =
            GenerationPurposeId.of("world:v14-phase-cluster-axis");
    private static final GenerationPurposeId CLUSTER_ELONGATION =
            GenerationPurposeId.of("world:v14-phase-cluster-elongation");
    private static final GenerationPurposeId GEOGRAPHY =
            GenerationPurposeId.of("world:v14-phase-geography");
    private static final GenerationPurposeId WARP_X =
            GenerationPurposeId.of("world:v14-phase-coast-warp-x");
    private static final GenerationPurposeId WARP_Y =
            GenerationPurposeId.of("world:v14-phase-coast-warp-y");
    private static final GenerationPurposeId COAST_DETAIL =
            GenerationPurposeId.of("world:v14-phase-coast-detail");

    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;
    private static final double TWO_PI = StrictMath.PI * 2d;

    /*
     * These are deliberately algorithm-private while this replacement is under visual acceptance.
     * If manual acceptance proves them to be stable model policy they can graduate into the recipe.
     */
    private static final int GRAPH_NEIGHBORS = 8;
    private static final int GRAPH_SEARCH_RADIUS = 2;
    private static final int REGULARIZATION_PASSES = 6;
    private static final double PHASE_SELF_WEIGHT = 0.38d;
    private static final double DIFFUSION_WEIGHT = 0.76d;
    private static final double FORCING_WEIGHT = 1d - DIFFUSION_WEIGHT;
    private static final double GEOGRAPHY_NOISE_AMPLITUDE = 0.24d;
    private static final double MAX_CLUSTER_ELONGATION = 0.28d;
    private static final double SEPARATOR_STRENGTH = 0.72d;
    private static final double KERNEL_RADIUS_IN_SPACING = 2.70d;
    private static final double EXTERNAL_OCEAN_KERNEL_WEIGHT = 1.75d;
    private static final double WARP_ATTENUATION = 0.32d;
    private static final double DETAIL_ATTENUATION = 0.10d;

    private RegularizedGraphLandmassSilhouetteAlgorithm() {
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
                    PPM);
        }

        GenerationRandom random = GenerationRandom.from(genesis);
        LandmassRandomStreams streams = LandmassRandomStreams.bind(random);
        ControlGraph graph = createControlGraph(
                streams,
                width,
                height,
                boundary,
                calibration,
                recipe.scaffold());

        int desiredSites = desiredLandSiteCount(
                graph,
                boundary.maximumLandCells(),
                area,
                calibration.fragmentationPpm(),
                recipe.growth());

        int clusterCount = Math.min(
                Math.min(calibration.landClusterCount(), desiredSites),
                graph.interiorCount());
        int[] seeds = chooseSeparatedSeeds(streams, graph, clusterCount);
        GeographicForcing forcing = resolveGeographicForcing(
                streams,
                graph,
                seeds,
                calibration.fragmentationPpm());

        boolean[] phase = initializePhase(graph, forcing, seeds, desiredSites);
        for (int pass = 0; pass < REGULARIZATION_PASSES; pass++) {
            phase = regularizePhase(
                    graph,
                    phase,
                    forcing,
                    seeds,
                    desiredSites);
        }

        CoastField coast = materializeImplicitCoast(
                streams,
                graph,
                phase,
                width,
                height,
                boundary,
                recipe.scaffold(),
                recipe.coast());

        CoastField relaxed = new CoastField(CoastFieldRelaxationAlgorithm.standard().relax(
                coast.score(),
                width,
                height,
                boundary.minimumOceanMarginCells(),
                graph.spacingCells(),
                recipe.relaxation()));

        return materializeSilhouette(
                bounds,
                relaxed,
                boundary.maximumLandCells(),
                PPM);
    }

    private static ControlGraph createControlGraph(
            LandmassRandomStreams streams,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe.ScaffoldPolicy policy) {
        int spacing = calibration.scaffoldSpacingCells();
        int columns = Math.max(3, Math.floorDiv(width + spacing - 1, spacing) + 4);
        int rows = Math.max(3, Math.floorDiv(height + spacing - 1, spacing) + 4);
        int count = Math.multiplyExact(columns, rows);
        double[] x = new double[count];
        double[] y = new double[count];
        boolean[] forcedOcean = new boolean[count];

        double jitter = spacing * policy.siteJitterPpm() / (double) PPM;
        double oceanBand = boundary.minimumOceanMarginCells()
                + spacing * policy.oceanSeedBandSpacingPpm() / (double) PPM;

        int interiorCount = 0;
        int index = 0;
        for (int gy = 0; gy < rows; gy++) {
            int latticeY = gy - 2;
            double baseY = (latticeY + 0.5d) * spacing;
            for (int gx = 0; gx < columns; gx++) {
                int latticeX = gx - 2;
                double baseX = (latticeX + 0.5d) * spacing;
                x[index] = baseX
                        + centeredUnit(streams.siteJitter(), latticeX, latticeY, 0L) * jitter;
                y[index] = baseY
                        + centeredUnit(streams.siteJitter(), latticeX, latticeY, 1L) * jitter;
                forcedOcean[index] = x[index] < oceanBand
                        || y[index] < oceanBand
                        || x[index] > width - 1d - oceanBand
                        || y[index] > height - 1d - oceanBand;
                if (!forcedOcean[index]) interiorCount++;
                index++;
            }
        }

        int[][] neighbors = new int[count][];
        double[][] neighborWeights = new double[count][];
        for (int site = 0; site < count; site++) {
            NeighborSet set = nearestLocalNeighbors(
                    site,
                    columns,
                    rows,
                    x,
                    y);
            neighbors[site] = set.sites();
            neighborWeights[site] = set.weights();
        }

        return new ControlGraph(
                columns,
                rows,
                spacing,
                x,
                y,
                forcedOcean,
                neighbors,
                neighborWeights,
                interiorCount);
    }

    private static NeighborSet nearestLocalNeighbors(
            int site,
            int columns,
            int rows,
            double[] x,
            double[] y) {
        int gx = site % columns;
        int gy = site / columns;
        CandidateNeighbor[] candidates =
                new CandidateNeighbor[(GRAPH_SEARCH_RADIUS * 2 + 1) * (GRAPH_SEARCH_RADIUS * 2 + 1) - 1];
        int count = 0;

        for (int oy = -GRAPH_SEARCH_RADIUS; oy <= GRAPH_SEARCH_RADIUS; oy++) {
            for (int ox = -GRAPH_SEARCH_RADIUS; ox <= GRAPH_SEARCH_RADIUS; ox++) {
                if (ox == 0 && oy == 0) continue;
                int nx = gx + ox;
                int ny = gy + oy;
                if (nx < 0 || nx >= columns || ny < 0 || ny >= rows) continue;
                int next = ny * columns + nx;
                double dx = x[next] - x[site];
                double dy = y[next] - y[site];
                double distanceSquared = dx * dx + dy * dy;
                if (!(distanceSquared > 0d)) continue;
                candidates[count++] = new CandidateNeighbor(next, distanceSquared);
            }
        }

        Arrays.sort(candidates, 0, count, Comparator
                .comparingDouble(CandidateNeighbor::distanceSquared)
                .thenComparingInt(CandidateNeighbor::site));

        int selected = Math.min(GRAPH_NEIGHBORS, count);
        int[] sites = new int[selected];
        double[] weights = new double[selected];
        double weightSum = 0d;
        for (int candidateIndex = 0; candidateIndex < selected; candidateIndex++) {
            CandidateNeighbor candidate = candidates[candidateIndex];
            sites[candidateIndex] = candidate.site();
            weights[candidateIndex] = 1d / StrictMath.sqrt(candidate.distanceSquared());
            weightSum += weights[candidateIndex];
        }
        if (weightSum > 0d) {
            for (int candidateIndex = 0; candidateIndex < selected; candidateIndex++) {
                weights[candidateIndex] /= weightSum;
            }
        }
        return new NeighborSet(sites, weights);
    }

    private static int desiredLandSiteCount(
            ControlGraph graph,
            int maximumLandCells,
            int worldArea,
            int fragmentationPpm,
            LandmassSilhouetteRecipe.GrowthPolicy policy) {
        if (graph.interiorCount() == 0 || maximumLandCells <= 0) return 0;

        double fragmentation = fragmentationPpm / (double) PPM;
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
        return Math.max(1, Math.min(
                graph.interiorCount(),
                (int) StrictMath.ceil(graph.interiorCount() * desiredFraction)));
    }

    private static int[] chooseSeparatedSeeds(
            LandmassRandomStreams streams,
            ControlGraph graph,
            int clusterCount) {
        if (clusterCount <= 0) return new int[0];

        int[] candidates = new int[graph.interiorCount()];
        int candidateCount = 0;
        for (int site = 0; site < graph.x().length; site++) {
            if (!graph.forcedOcean()[site]) candidates[candidateCount++] = site;
        }
        if (candidateCount == 0) {
            throw new IllegalStateException("landmass control graph has no interior seed sites");
        }

        int[] seeds = new int[clusterCount];
        int first = candidates[0];
        int bestRank = -1;
        for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
            int site = candidates[candidateIndex];
            int rank = randomPpm(streams.firstSeed(), site, 0L, 0L);
            if (rank > bestRank || rank == bestRank && site < first) {
                bestRank = rank;
                first = site;
            }
        }
        seeds[0] = first;

        for (int seedIndex = 1; seedIndex < clusterCount; seedIndex++) {
            int selected = -1;
            double selectedScore = Double.NEGATIVE_INFINITY;
            for (int candidateIndex = 0; candidateIndex < candidateCount; candidateIndex++) {
                int site = candidates[candidateIndex];
                if (contains(seeds, seedIndex, site)) continue;

                double minimumDistanceSquared = Double.POSITIVE_INFINITY;
                for (int existing = 0; existing < seedIndex; existing++) {
                    int seed = seeds[existing];
                    double dx = graph.x()[site] - graph.x()[seed];
                    double dy = graph.y()[site] - graph.y()[seed];
                    minimumDistanceSquared = Math.min(
                            minimumDistanceSquared,
                            dx * dx + dy * dy);
                }
                double jitter = 0.92d + 0.16d
                        * randomPpm(streams.seedJitter(), site, seedIndex, 0L) / PPM;
                double score = minimumDistanceSquared * jitter;
                if (score > selectedScore || score == selectedScore && site < selected) {
                    selectedScore = score;
                    selected = site;
                }
            }
            if (selected < 0) {
                throw new IllegalStateException("unable to place separated landmass seed");
            }
            seeds[seedIndex] = selected;
        }
        return seeds;
    }

    private static GeographicForcing resolveGeographicForcing(
            LandmassRandomStreams streams,
            ControlGraph graph,
            int[] seeds,
            int fragmentationPpm) {
        double[] score = new double[graph.x().length];
        double[] separationPenalty = new double[graph.x().length];
        Arrays.fill(score, Double.NEGATIVE_INFINITY);

        double[] axisX = new double[seeds.length];
        double[] axisY = new double[seeds.length];
        double[] elongation = new double[seeds.length];
        for (int cluster = 0; cluster < seeds.length; cluster++) {
            double angle = randomPpm(streams.clusterAxis(), cluster, 0L, 0L)
                    / (double) PPM * TWO_PI;
            axisX[cluster] = StrictMath.cos(angle);
            axisY[cluster] = StrictMath.sin(angle);
            elongation[cluster] = centeredUnit(
                    streams.clusterElongation(),
                    cluster,
                    0L,
                    0L) * MAX_CLUSTER_ELONGATION;
        }

        double geographyScale = Math.max(3d, graph.spacingCells() * 3.8d);
        double fragmentation = fragmentationPpm / (double) PPM;

        for (int site = 0; site < graph.x().length; site++) {
            if (graph.forcedOcean()[site] || seeds.length == 0) {
                score[site] = -2d;
                separationPenalty[site] = 1d;
                continue;
            }

            double bestDistance = Double.POSITIVE_INFINITY;
            double secondDistance = Double.POSITIVE_INFINITY;
            for (int cluster = 0; cluster < seeds.length; cluster++) {
                int seed = seeds[cluster];
                double dx = graph.x()[site] - graph.x()[seed];
                double dy = graph.y()[site] - graph.y()[seed];
                double along = dx * axisX[cluster] + dy * axisY[cluster];
                double across = -dx * axisY[cluster] + dy * axisX[cluster];
                double stretch = elongation[cluster];
                double scaledAlong = along / (1d + stretch);
                double scaledAcross = across / (1d - stretch);
                double distance = StrictMath.hypot(scaledAlong, scaledAcross);

                if (distance < bestDistance) {
                    secondDistance = bestDistance;
                    bestDistance = distance;
                } else if (distance < secondDistance) {
                    secondDistance = distance;
                }
            }

            double normalizedDistance = bestDistance / Math.max(1d, graph.spacingCells());
            double broadNoise = smoothNoise(
                    streams.geography(),
                    graph.x()[site],
                    graph.y()[site],
                    geographyScale);
            double geographicScore = -normalizedDistance
                    + broadNoise * GEOGRAPHY_NOISE_AMPLITUDE;

            double separator = 0d;
            if (seeds.length > 1 && Double.isFinite(secondDistance)) {
                double gap = Math.max(0d, secondDistance - bestDistance);
                double normalizedGap = gap / Math.max(1d, graph.spacingCells());
                separator = fragmentation
                        * StrictMath.exp(-normalizedGap / 0.72d)
                        * SEPARATOR_STRENGTH;
            }
            score[site] = geographicScore - separator;
            separationPenalty[site] = separator;
        }

        return new GeographicForcing(score, separationPenalty);
    }

    private static boolean[] initializePhase(
            ControlGraph graph,
            GeographicForcing forcing,
            int[] seeds,
            int desiredSites) {
        double[] score = forcing.score().clone();
        protectSeeds(score, seeds);
        return selectVolume(graph, score, desiredSites);
    }

    private static boolean[] regularizePhase(
            ControlGraph graph,
            boolean[] phase,
            GeographicForcing forcing,
            int[] seeds,
            int desiredSites) {
        double[] rankingScore = new double[phase.length];

        for (int site = 0; site < phase.length; site++) {
            if (graph.forcedOcean()[site]) {
                rankingScore[site] = Double.NEGATIVE_INFINITY;
                continue;
            }

            double neighborPhase = 0d;
            int[] neighbors = graph.neighbors()[site];
            double[] weights = graph.neighborWeights()[site];
            for (int neighborIndex = 0; neighborIndex < neighbors.length; neighborIndex++) {
                int neighbor = neighbors[neighborIndex];
                double value = !graph.forcedOcean()[neighbor] && phase[neighbor] ? 1d : 0d;
                neighborPhase += value * weights[neighborIndex];
            }

            double self = phase[site] ? 1d : 0d;
            double diffused = PHASE_SELF_WEIGHT * self
                    + (1d - PHASE_SELF_WEIGHT) * neighborPhase;

            double forcingValue = normalizedForcing(forcing.score()[site]);
            rankingScore[site] = DIFFUSION_WEIGHT * diffused
                    + FORCING_WEIGHT * forcingValue
                    - forcing.separationPenalty()[site] * 0.10d;
        }

        protectSeeds(rankingScore, seeds);
        return selectVolume(graph, rankingScore, desiredSites);
    }

    private static double normalizedForcing(double score) {
        if (!Double.isFinite(score)) return 0d;
        return 1d / (1d + StrictMath.exp(-score));
    }

    private static void protectSeeds(double[] score, int[] seeds) {
        for (int seedIndex = 0; seedIndex < seeds.length; seedIndex++) {
            score[seeds[seedIndex]] = 10d - seedIndex * 1.0e-9d;
        }
    }

    private static boolean[] selectVolume(
            ControlGraph graph,
            double[] score,
            int desiredSites) {
        RankedSite[] ranked = new RankedSite[graph.interiorCount()];
        int count = 0;
        for (int site = 0; site < graph.x().length; site++) {
            if (graph.forcedOcean()[site]) continue;
            ranked[count++] = new RankedSite(site, score[site]);
        }
        Arrays.sort(ranked, Comparator
                .comparingDouble(RankedSite::score)
                .reversed()
                .thenComparingInt(RankedSite::site));

        boolean[] phase = new boolean[graph.x().length];
        int selected = Math.min(desiredSites, ranked.length);
        for (int rank = 0; rank < selected; rank++) {
            if (!Double.isFinite(ranked[rank].score())) {
                throw new IllegalStateException(
                        "regularized land phase left an interior control site unreachable");
            }
            phase[ranked[rank].site()] = true;
        }
        return phase;
    }

    private static CoastField materializeImplicitCoast(
            LandmassRandomStreams streams,
            ControlGraph graph,
            boolean[] phase,
            int width,
            int height,
            LandmassBoundaryCalibration boundary,
            LandmassSilhouetteRecipe.ScaffoldPolicy scaffold,
            LandmassSilhouetteRecipe.CoastPolicy coast) {
        int area = Math.multiplyExact(width, height);
        double[] score = new double[area];

        double spacing = graph.spacingCells();
        double kernelRadius = Math.max(3d, spacing * KERNEL_RADIUS_IN_SPACING);
        double maximumJitterInSpacing = scaffold.siteJitterPpm() / (double) PPM;
        /*
         * A control point at lattice offset k starts half a spacing from the raster cell's lattice
         * origin and can move by at most siteJitter. Any integer k with
         * |k| >= kernelRadius/spacing + 0.5 + jitter can never enter the compact-support kernel.
         */
        int latticeRadius = Math.max(
                1,
                (int) StrictMath.ceil(
                        kernelRadius / spacing + 0.5d + maximumJitterInSpacing) - 1);

        double warpScale = Math.max(
                3d,
                spacing * coast.warpScaleSpacingPpm() / (double) PPM);
        double detailScale = Math.max(
                3d,
                spacing * coast.detailScaleSpacingPpm() / (double) PPM);
        double warpAmplitude = spacing
                * coast.warpAmplitudeSpacingPpm() / (double) PPM
                * WARP_ATTENUATION;
        double detailAmplitude = spacing
                * coast.detailAmplitudeSpacingPpm() / (double) PPM
                * DETAIL_ATTENUATION;
        SmoothDoubleNoiseRowSampler warpXNoise = new SmoothDoubleNoiseRowSampler(
                streams.warpX(), 0, width - 1, warpScale);
        SmoothDoubleNoiseRowSampler warpYNoise = new SmoothDoubleNoiseRowSampler(
                streams.warpY(), 0, width - 1, warpScale);
        SmoothDoubleNoiseRowSampler detailNoise = new SmoothDoubleNoiseRowSampler(
                streams.coastDetail(), 0, width - 1, detailScale);

        int guaranteedMargin = boundary.minimumOceanMarginCells();
        double deformationRampCells = Math.max(1d, spacing);

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                int edgeDistance = edgeDistance(localX, localY, width, height);
                double deformationCoordinate =
                        (edgeDistance - guaranteedMargin) / deformationRampCells;
                double deformationFactor = smooth(clamp01(deformationCoordinate));

                double px = localX
                        + warpXNoise.sampleAt(localX, localY)
                                * warpAmplitude * deformationFactor;
                double py = localY
                        + warpYNoise.sampleAt(localX, localY)
                                * warpAmplitude * deformationFactor;

                int approximateGx = (int) StrictMath.floor(px / spacing) + 2;
                int approximateGy = (int) StrictMath.floor(py / spacing) + 2;

                double signed = 0d;
                double totalWeight = 0d;

                for (int gy = approximateGy - latticeRadius;
                        gy <= approximateGy + latticeRadius;
                        gy++) {
                    if (gy < 0 || gy >= graph.rows()) continue;
                    for (int gx = approximateGx - latticeRadius;
                            gx <= approximateGx + latticeRadius;
                            gx++) {
                        if (gx < 0 || gx >= graph.columns()) continue;
                        int site = gy * graph.columns() + gx;
                        double dx = px - graph.x()[site];
                        double dy = py - graph.y()[site];
                        double distance = StrictMath.hypot(dx, dy);
                        if (distance >= kernelRadius) continue;
                        double weight = wendlandC2(distance / kernelRadius);
                        if (!(weight > 0d)) continue;
                        signed += weight * (phase[site] ? 1d : -1d);
                        totalWeight += weight;
                    }
                }

                /* External ocean is a negative boundary condition in the same implicit field. */
                double boundaryRadius = Math.max(2d, spacing * 1.15d);
                double boundaryDistance = Math.max(0d, edgeDistance);
                if (boundaryDistance < boundaryRadius) {
                    double weight = wendlandC2(boundaryDistance / boundaryRadius)
                            * EXTERNAL_OCEAN_KERNEL_WEIGHT;
                    signed -= weight;
                    totalWeight += weight;
                }

                double implicit = totalWeight > 0d ? signed / totalWeight : -1d;
                double detail = detailNoise.sampleAt(localX, localY)
                        * detailAmplitude * deformationFactor;

                double coastScore = implicit * spacing + detail;
                if (edgeDistance < guaranteedMargin && coastScore >= 0d) {
                    throw new IllegalStateException(
                            "regularized land phase breached generated external-ocean boundary");
                }
                score[index++] = coastScore;
            }
        }

        return new CoastField(score);
    }

    private static double wendlandC2(double normalizedDistance) {
        if (!(normalizedDistance >= 0d) || normalizedDistance >= 1d) return 0d;
        double oneMinus = 1d - normalizedDistance;
        double square = oneMinus * oneMinus;
        return square * square * (4d * normalizedDistance + 1d);
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
            throw new IllegalStateException(
                    "regularized land phase produced no terrestrial support");
        }

        double cutoff = 0d;
        if (positiveCount > maximumLandCells) {
            cutoff = PositiveDoubleRadixSelector.select(
                    score,
                    positiveCount,
                    positiveCount - maximumLandCells);
        }

        boolean[] support = new boolean[score.length];
        int[] potentialPpm = new int[score.length];
        int supportCount = 0;
        double maximumInterior = 0d;
        for (int cellIndex = 0; cellIndex < score.length; cellIndex++) {
            if (!(score[cellIndex] > cutoff)) continue;
            support[cellIndex] = true;
            supportCount++;
            maximumInterior = Math.max(
                    maximumInterior,
                    score[cellIndex] - cutoff);
        }

        if (supportCount == 0 || supportCount > maximumLandCells) {
            throw new IllegalStateException(
                    "regularized land support violated finite-world land capacity");
        }

        double denominator = Math.max(0.000_001d, maximumInterior);
        for (int cellIndex = 0; cellIndex < score.length; cellIndex++) {
            if (!support[cellIndex]) continue;
            long normalized = StrictMath.round(
                    (score[cellIndex] - cutoff) / denominator * PPM);
            potentialPpm[cellIndex] = (int) Math.max(
                    1L,
                    Math.min((long) PPM, normalized));
        }

        return new LandmassSilhouette(
                bounds,
                support,
                potentialPpm,
                supportCount,
                influencePpm);
    }

    private static boolean contains(
            int[] values,
            int length,
            int target) {
        for (int index = 0; index < length; index++) {
            if (values[index] == target) return true;
        }
        return false;
    }

    private static double smoothNoise(
            GenerationRandom.BoundSampler sampler,
            double x,
            double y,
            double scale) {
        double gridX = x / scale;
        double gridY = y / scale;
        long x0 = (long) StrictMath.floor(gridX);
        long y0 = (long) StrictMath.floor(gridY);
        double tx = smooth(gridX - x0);
        double ty = smooth(gridY - y0);
        double a = centeredUnit(sampler, x0, y0, 0L);
        double b = centeredUnit(sampler, x0 + 1L, y0, 0L);
        double c = centeredUnit(sampler, x0, y0 + 1L, 0L);
        double d = centeredUnit(sampler, x0 + 1L, y0 + 1L, 0L);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
    }

    private static double centeredUnit(
            GenerationRandom.BoundSampler sampler,
            long x,
            long y,
            long ordinal) {
        return randomPpm(sampler, x, y, ordinal)
                / (double) PPM * 2d - 1d;
    }

    private static int randomPpm(
            GenerationRandom.BoundSampler sampler,
            long x,
            long y,
            long ordinal) {
        int sample = (int) ((sampler.sampleLong(
                x,
                y,
                0L,
                ordinal) >>> 48) & SAMPLE_MAX);
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    private static double lerp(
            double from,
            double to,
            double amount) {
        return from + (to - from) * amount;
    }

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static int edgeDistance(
            int x,
            int y,
            int width,
            int height) {
        return Math.min(
                Math.min(x, width - 1 - x),
                Math.min(y, height - 1 - y));
    }

    private record LandmassRandomStreams(
            GenerationRandom.BoundSampler siteJitter,
            GenerationRandom.BoundSampler firstSeed,
            GenerationRandom.BoundSampler seedJitter,
            GenerationRandom.BoundSampler clusterAxis,
            GenerationRandom.BoundSampler clusterElongation,
            GenerationRandom.BoundSampler geography,
            GenerationRandom.BoundSampler warpX,
            GenerationRandom.BoundSampler warpY,
            GenerationRandom.BoundSampler coastDetail) {
        private static LandmassRandomStreams bind(GenerationRandom random) {
            return new LandmassRandomStreams(
                    random.bind(ElevationGenerationStage.STAGE_ID, SITE_JITTER),
                    random.bind(ElevationGenerationStage.STAGE_ID, FIRST_SEED),
                    random.bind(ElevationGenerationStage.STAGE_ID, SEED_JITTER),
                    random.bind(ElevationGenerationStage.STAGE_ID, CLUSTER_AXIS),
                    random.bind(ElevationGenerationStage.STAGE_ID, CLUSTER_ELONGATION),
                    random.bind(ElevationGenerationStage.STAGE_ID, GEOGRAPHY),
                    random.bind(ElevationGenerationStage.STAGE_ID, WARP_X),
                    random.bind(ElevationGenerationStage.STAGE_ID, WARP_Y),
                    random.bind(ElevationGenerationStage.STAGE_ID, COAST_DETAIL));
        }
    }

    private record ControlGraph(
            int columns,
            int rows,
            int spacingCells,
            double[] x,
            double[] y,
            boolean[] forcedOcean,
            int[][] neighbors,
            double[][] neighborWeights,
            int interiorCount) {
    }

    private record NeighborSet(
            int[] sites,
            double[] weights) {
    }

    private record CandidateNeighbor(
            int site,
            double distanceSquared) {
    }

    private record GeographicForcing(
            double[] score,
            double[] separationPenalty) {
    }

    private record RankedSite(
            int site,
            double score) {
    }

    private record CoastField(double[] score) {
    }
}
