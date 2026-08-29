package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Compact immutable V14 continent plan.
 *
 * <p>The accepted V14 control graph and coastline mathematics are preserved. Whole-world raster
 * materialization is replaced by bounded row streaming plus exact radix selection of the global
 * coastline cutoff.</p>
 */
public final class V14LandmassPlan {
    private static final int PPM = 1_000_000;
    private static final int SAMPLE_MAX = 65_535;
    private static final double TWO_PI = StrictMath.PI * 2d;

    private static final String SITE_JITTER = "world:v14-phase-site-jitter";
    private static final String FIRST_SEED = "world:v14-phase-first-seed";
    private static final String SEED_JITTER = "world:v14-phase-seed-jitter";
    private static final String CLUSTER_AXIS = "world:v14-phase-cluster-axis";
    private static final String CLUSTER_ELONGATION = "world:v14-phase-cluster-elongation";
    private static final String GEOGRAPHY = "world:v14-phase-geography";
    private static final String WARP_X = "world:v14-phase-coast-warp-x";
    private static final String WARP_Y = "world:v14-phase-coast-warp-y";
    private static final String COAST_DETAIL = "world:v14-phase-coast-detail";

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

    private static final int GUARANTEED_OCEAN_EDGE_CELLS = 1;
    private static final int BASE_MAXIMUM_LAND_PPM = 420_000;
    private static final int MAXIMUM_LAND_RANGE_PPM = 180_000;
    private static final int LAND_HALF_SATURATION_CELLS = 256;

    private static final int MINIMUM_SCAFFOLD_SPACING = 4;
    private static final int MINIMUM_SPACING_WORLD_PPM = 70_000;
    private static final int MAXIMUM_SPACING_WORLD_PPM = 220_000;
    private static final int FRAGMENTATION_SPACING_COMPRESSION_PPM = 550_000;
    private static final int SITE_JITTER_PPM = 380_000;
    private static final int OCEAN_SEED_BAND_SPACING_PPM = 550_000;

    private static final int MAXIMUM_CLUSTER_COUNT = 8;
    private static final int COHESIVE_SUPPORT_EXPANSION_PPM = 1_600_000;
    private static final int FRAGMENTED_SUPPORT_EXPANSION_PPM = 1_050_000;
    private static final int COHESIVE_MAXIMUM_SUPPORT_PPM = 820_000;
    private static final int FRAGMENTED_MAXIMUM_SUPPORT_PPM = 650_000;

    private static final int COAST_WARP_SCALE_SPACING_PPM = 2_400_000;
    private static final int COAST_DETAIL_SCALE_SPACING_PPM = 750_000;
    private static final int COAST_WARP_AMPLITUDE_SPACING_PPM = 260_000;
    private static final int COAST_DETAIL_AMPLITUDE_SPACING_PPM = 80_000;

    private static final int COAST_RELAXATION_PASSES = 2;
    private static final int COAST_RELAXATION_BAND_WIDTH_SPACING_PPM = 160_000;
    private static final int COAST_RELAXATION_SELF_WEIGHT_PPM = 500_000;
    private static final int COAST_RELAXATION_ORTHOGONAL_WEIGHT_PPM = 90_000;
    private static final int COAST_RELAXATION_DIAGONAL_WEIGHT_PPM = 35_000;
    private static final int COAST_RELAXATION_MAX_SHIFT_PPM = 450_000;

    public static final int SILHOUETTE_INFLUENCE_PPM = 900_000;

    private final ContinuumWorldDomain domain;
    private final LegacyV15Random random;
    private final ControlGraph graph;
    private final boolean[] phase;
    private final int guaranteedMargin;
    private final long maximumLandCells;
    private final double cutoff;
    private final double maximumInterior;
    private final long supportCellCount;

    private V14LandmassPlan(
            ContinuumWorldDomain domain,
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            long maximumLandCells,
            double cutoff,
            double maximumInterior,
            long supportCellCount) {
        this.domain = domain;
        this.random = random;
        this.graph = graph;
        this.phase = phase;
        this.guaranteedMargin = guaranteedMargin;
        this.maximumLandCells = maximumLandCells;
        this.cutoff = cutoff;
        this.maximumInterior = maximumInterior;
        this.supportCellCount = supportCellCount;
    }

    public static V14LandmassPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            V12TerrainCalibration terrain) {
        if (domain == null || definition == null || terrain == null) {
            throw new IllegalArgumentException("V14 landmass inputs must not be null");
        }
        if (domain.width() != terrain.width() || domain.height() != terrain.height()) {
            throw new IllegalArgumentException("terrain calibration must match the Continuum domain");
        }

        LegacyV15Random random = new LegacyV15Random(seed);
        int limitingSpan = Math.min(terrain.width(), terrain.height());
        if (limitingSpan < 8) {
            throw new IllegalArgumentException("V14 landmass needs at least an 8-cell span");
        }

        int geometricMaximum = Math.max(1, (limitingSpan - 4) / 3);
        int guaranteedMargin = Math.min(geometricMaximum, GUARANTEED_OCEAN_EDGE_CELLS);
        int maximumLandPpm = BASE_MAXIMUM_LAND_PPM
                + Math.toIntExact((long) MAXIMUM_LAND_RANGE_PPM * limitingSpan
                        / ((long) limitingSpan + LAND_HALF_SATURATION_CELLS));
        long maximumLandCells = scaledCount(terrain.area(), maximumLandPpm);

        int scalePpm = V12TerrainCalibration.ppm(definition.landmassScale());
        int fragmentationPpm = terrain.fragmentationPpm();
        long spacingWorldPpm = MINIMUM_SPACING_WORLD_PPM
                + (long) (MAXIMUM_SPACING_WORLD_PPM - MINIMUM_SPACING_WORLD_PPM)
                        * scalePpm / PPM;
        long compressionPpm = (long) fragmentationPpm
                * FRAGMENTATION_SPACING_COMPRESSION_PPM / PPM;
        spacingWorldPpm = spacingWorldPpm * (PPM - compressionPpm) / PPM;
        int spacing = Math.max(
                MINIMUM_SCAFFOLD_SPACING,
                Math.toIntExact((long) limitingSpan * spacingWorldPpm / PPM));

        int requestedClusters = 1 + Math.toIntExact(
                ((long) fragmentationPpm * (MAXIMUM_CLUSTER_COUNT - 1) + PPM / 2L) / PPM);
        int approximateColumns = Math.max(1, Math.floorDiv(terrain.width() + spacing - 1, spacing));
        int approximateRows = Math.max(1, Math.floorDiv(terrain.height() + spacing - 1, spacing));
        int structuralCapacity = Math.max(
                1, Math.multiplyExact(approximateColumns, approximateRows) / 3);
        int clusterCount = Math.max(1, Math.min(requestedClusters, structuralCapacity));

        ControlGraph graph = createControlGraph(
                random, terrain.width(), terrain.height(), guaranteedMargin, spacing);
        int desiredSites = desiredLandSiteCount(
                graph, maximumLandCells, terrain.area(), fragmentationPpm);
        clusterCount = Math.min(Math.min(clusterCount, desiredSites), graph.interiorCount());
        int[] seeds = chooseSeparatedSeeds(random, graph, clusterCount);
        GeographicForcing forcing = resolveGeographicForcing(random, graph, seeds, fragmentationPpm);
        boolean[] phase = initializePhase(graph, forcing, seeds, desiredSites);
        for (int pass = 0; pass < REGULARIZATION_PASSES; pass++) {
            phase = regularizePhase(graph, phase, forcing, seeds, desiredSites);
        }

        Preparation preparation = prepareRasterFacts(
                domain, random, graph, phase, guaranteedMargin, maximumLandCells);
        return new V14LandmassPlan(
                domain,
                random,
                graph,
                phase,
                guaranteedMargin,
                maximumLandCells,
                preparation.cutoff(),
                preparation.maximumInterior(),
                preparation.supportCellCount());
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public long maximumLandCells() {
        return maximumLandCells;
    }

    public long supportCellCount() {
        return supportCellCount;
    }

    public boolean supports(long x, long y) {
        requireCoordinate(x, y);
        return relaxedCoastScoreAt(Math.toIntExact(x), Math.toIntExact(y)) > cutoff;
    }

    public int potentialPpmAt(long x, long y) {
        requireCoordinate(x, y);
        return potentialPpmFromScore(relaxedCoastScoreAt(Math.toIntExact(x), Math.toIntExact(y)));
    }

    PotentialRowCursor potentialRowCursor() {
        return new PotentialRowCursor();
    }

    private int potentialPpmFromScore(double score) {
        if (!(score > cutoff)) return 0;
        long normalized = StrictMath.round((score - cutoff) / maximumInterior * PPM);
        return (int) Math.max(1L, Math.min((long) PPM, normalized));
    }

    final class PotentialRowCursor {
        private final RelaxedRowCache rows = new RelaxedRowCache(
                random, graph, phase, guaranteedMargin, domain);
        private final int width = Math.toIntExact(domain.width());

        void fill(int y, int[] target) {
            if (target == null || target.length < width) {
                throw new IllegalArgumentException("V14 potential row target must fit domain width");
            }
            double[] scores = rows.row(COAST_RELAXATION_PASSES, y);
            for (int x = 0; x < width; x++) {
                target[x] = potentialPpmFromScore(scores[x]);
            }
        }
    }

    double relaxedCoastScoreAt(int x, int y) {
        return relaxedCoastScoreAt(
                random,
                graph,
                phase,
                guaranteedMargin,
                Math.toIntExact(domain.width()),
                Math.toIntExact(domain.height()),
                COAST_RELAXATION_PASSES,
                x,
                y);
    }

    private static double relaxedCoastScoreAt(
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            int width,
            int height,
            int pass,
            int x,
            int y) {
        if (pass == 0) {
            return rawCoastScoreAt(random, graph, phase, guaranteedMargin, width, height, x, y);
        }

        double center = relaxedCoastScoreAt(
                random, graph, phase, guaranteedMargin, width, height, pass - 1, x, y);
        if (edgeDistance(x, y, width, height) < guaranteedMargin) return center;

        double bandWidth = Math.max(
                1.5d,
                graph.spacingCells() * COAST_RELAXATION_BAND_WIDTH_SPACING_PPM / (double) PPM);
        if (!Double.isFinite(center) || StrictMath.abs(center) > bandWidth) return center;

        double weighted = center * COAST_RELAXATION_SELF_WEIGHT_PPM;
        long totalWeight = COAST_RELAXATION_SELF_WEIGHT_PPM;
        for (int oy = -1; oy <= 1; oy++) {
            int ny = y + oy;
            if (ny < 0 || ny >= height) continue;
            for (int ox = -1; ox <= 1; ox++) {
                if (ox == 0 && oy == 0) continue;
                int nx = x + ox;
                if (nx < 0 || nx >= width) continue;
                double neighbor = relaxedCoastScoreAt(
                        random,
                        graph,
                        phase,
                        guaranteedMargin,
                        width,
                        height,
                        pass - 1,
                        nx,
                        ny);
                if (!Double.isFinite(neighbor)) continue;
                int weight = ox == 0 || oy == 0
                        ? COAST_RELAXATION_ORTHOGONAL_WEIGHT_PPM
                        : COAST_RELAXATION_DIAGONAL_WEIGHT_PPM;
                weighted += neighbor * weight;
                totalWeight += weight;
            }
        }
        double target = weighted / totalWeight;
        double maximumShift = COAST_RELAXATION_MAX_SHIFT_PPM / (double) PPM;
        double shift = Math.max(-maximumShift, Math.min(maximumShift, target - center));
        return center + shift;
    }

    private void requireCoordinate(long x, long y) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside the V14 landmass domain");
        }
    }

    private static Preparation prepareRasterFacts(
            ContinuumWorldDomain domain,
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            long maximumLandCells) {
        long[] histogram = new long[1 << 16];
        InitialRadixScan initial = initialRadixScan(
                domain, random, graph, phase, guaranteedMargin, histogram);
        if (initial.positiveCount() == 0L) {
            throw new IllegalStateException("regularized V14 land phase produced no terrestrial support");
        }

        if (initial.positiveCount() <= maximumLandCells) {
            return new Preparation(0d, initial.maximumPositive(), initial.positiveCount());
        }

        long targetRank = initial.positiveCount() - maximumLandCells;
        long rank = targetRank;
        long prefix = 0L;
        long mask = 0L;
        RadixBucket selected = selectRadixBucket(histogram, rank);
        rank -= selected.before();
        prefix |= (long) selected.bucket() << 48;
        mask |= 0xffffL << 48;

        for (int shift = 32; shift >= 0; shift -= 16) {
            Arrays.fill(histogram, 0L);
            fillRadixHistogram(
                    domain,
                    random,
                    graph,
                    phase,
                    guaranteedMargin,
                    prefix,
                    mask,
                    shift,
                    histogram);
            selected = selectRadixBucket(histogram, rank);
            rank -= selected.before();
            prefix |= (long) selected.bucket() << shift;
            mask |= 0xffffL << shift;
        }

        double cutoff = Double.longBitsToDouble(prefix);
        long lessThanCutoff = targetRank - rank;
        long supportCount = initial.positiveCount() - lessThanCutoff - selected.count();
        double maximumInterior = initial.maximumPositive() - cutoff;
        if (supportCount == 0L || supportCount > maximumLandCells) {
            throw new IllegalStateException("regularized V14 land support violated land capacity");
        }
        if (!(maximumInterior > 0d)) {
            throw new IllegalStateException("regularized V14 land support has no positive interior");
        }
        return new Preparation(cutoff, maximumInterior, supportCount);
    }

    private static InitialRadixScan initialRadixScan(
            ContinuumWorldDomain domain,
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            long[] highHistogram) {
        RelaxedRowCache rows = new RelaxedRowCache(random, graph, phase, guaranteedMargin, domain);
        long positiveCount = 0L;
        double maximumPositive = 0d;
        for (int y = 0; y < domain.height(); y++) {
            double[] row = rows.row(COAST_RELAXATION_PASSES, y);
            for (double value : row) {
                if (!(value > 0d)) continue;
                positiveCount++;
                maximumPositive = Math.max(maximumPositive, value);
                long bits = Double.doubleToRawLongBits(value);
                highHistogram[(int) ((bits >>> 48) & 0xffffL)]++;
            }
        }
        return new InitialRadixScan(positiveCount, maximumPositive);
    }

    private static void fillRadixHistogram(
            ContinuumWorldDomain domain,
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            long prefix,
            long mask,
            int shift,
            long[] histogram) {
        RelaxedRowCache rows = new RelaxedRowCache(random, graph, phase, guaranteedMargin, domain);
        for (int y = 0; y < domain.height(); y++) {
            double[] row = rows.row(COAST_RELAXATION_PASSES, y);
            for (double value : row) {
                if (!(value > 0d)) continue;
                long bits = Double.doubleToRawLongBits(value);
                if ((bits & mask) != prefix) continue;
                histogram[(int) ((bits >>> shift) & 0xffffL)]++;
            }
        }
    }

    private static RadixBucket selectRadixBucket(long[] histogram, long rank) {
        long before = 0L;
        for (int bucket = 0; bucket < histogram.length; bucket++) {
            long count = histogram[bucket];
            if (rank < before + count) {
                return new RadixBucket(bucket, before, count);
            }
            before += count;
        }
        throw new IllegalStateException("unable to resolve V14 coastline cutoff rank");
    }

    private static ControlGraph createControlGraph(
            LegacyV15Random random,
            int width,
            int height,
            int guaranteedMargin,
            int spacing) {
        int columns = Math.max(3, Math.floorDiv(width + spacing - 1, spacing) + 4);
        int rows = Math.max(3, Math.floorDiv(height + spacing - 1, spacing) + 4);
        int count = Math.multiplyExact(columns, rows);
        double[] x = new double[count];
        double[] y = new double[count];
        boolean[] forcedOcean = new boolean[count];

        double jitter = spacing * SITE_JITTER_PPM / (double) PPM;
        double oceanBand = guaranteedMargin + spacing * OCEAN_SEED_BAND_SPACING_PPM / (double) PPM;
        int interiorCount = 0;
        int index = 0;
        for (int gy = 0; gy < rows; gy++) {
            int latticeY = gy - 2;
            double baseY = (latticeY + 0.5d) * spacing;
            for (int gx = 0; gx < columns; gx++) {
                int latticeX = gx - 2;
                double baseX = (latticeX + 0.5d) * spacing;
                x[index] = baseX + centeredUnit(random, SITE_JITTER, latticeX, latticeY, 0L) * jitter;
                y[index] = baseY + centeredUnit(random, SITE_JITTER, latticeX, latticeY, 1L) * jitter;
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
            NeighborSet set = nearestLocalNeighbors(site, columns, rows, x, y);
            neighbors[site] = set.sites();
            neighborWeights[site] = set.weights();
        }
        return new ControlGraph(
                columns, rows, spacing, x, y, forcedOcean, neighbors, neighborWeights, interiorCount);
    }

    private static NeighborSet nearestLocalNeighbors(
            int site,
            int columns,
            int rows,
            double[] x,
            double[] y) {
        int gx = site % columns;
        int gy = site / columns;
        CandidateNeighbor[] candidates = new CandidateNeighbor[
                (GRAPH_SEARCH_RADIUS * 2 + 1) * (GRAPH_SEARCH_RADIUS * 2 + 1) - 1];
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
        for (int i = 0; i < selected; i++) {
            CandidateNeighbor candidate = candidates[i];
            sites[i] = candidate.site();
            weights[i] = 1d / StrictMath.sqrt(candidate.distanceSquared());
            weightSum += weights[i];
        }
        if (weightSum > 0d) {
            for (int i = 0; i < selected; i++) weights[i] /= weightSum;
        }
        return new NeighborSet(sites, weights);
    }

    private static int desiredLandSiteCount(
            ControlGraph graph,
            long maximumLandCells,
            long worldArea,
            int fragmentationPpm) {
        if (graph.interiorCount() == 0 || maximumLandCells <= 0L) return 0;
        double fragmentation = fragmentationPpm / (double) PPM;
        double expansion = lerp(
                COHESIVE_SUPPORT_EXPANSION_PPM / (double) PPM,
                FRAGMENTED_SUPPORT_EXPANSION_PPM / (double) PPM,
                fragmentation);
        double maximumSupport = lerp(
                COHESIVE_MAXIMUM_SUPPORT_PPM / (double) PPM,
                FRAGMENTED_MAXIMUM_SUPPORT_PPM / (double) PPM,
                fragmentation);
        double requestedWorldFraction = maximumLandCells / (double) worldArea;
        double desiredFraction = Math.min(maximumSupport, requestedWorldFraction * expansion);
        return Math.max(1, Math.min(
                graph.interiorCount(),
                (int) StrictMath.ceil(graph.interiorCount() * desiredFraction)));
    }

    private static int[] chooseSeparatedSeeds(
            LegacyV15Random random,
            ControlGraph graph,
            int clusterCount) {
        if (clusterCount <= 0) return new int[0];
        int[] candidates = new int[graph.interiorCount()];
        int candidateCount = 0;
        for (int site = 0; site < graph.x().length; site++) {
            if (!graph.forcedOcean()[site]) candidates[candidateCount++] = site;
        }
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
                    double dx = graph.x()[site] - graph.x()[seed];
                    double dy = graph.y()[site] - graph.y()[seed];
                    minimumDistanceSquared = Math.min(minimumDistanceSquared, dx * dx + dy * dy);
                }
                double jitter = 0.92d + 0.16d * randomPpm(random, SEED_JITTER, site, seedIndex, 0L) / PPM;
                double score = minimumDistanceSquared * jitter;
                if (score > selectedScore || score == selectedScore && site < selected) {
                    selectedScore = score;
                    selected = site;
                }
            }
            if (selected < 0) throw new IllegalStateException("unable to place V14 landmass seed");
            seeds[seedIndex] = selected;
        }
        return seeds;
    }

    private static GeographicForcing resolveGeographicForcing(
            LegacyV15Random random,
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
            double angle = randomPpm(random, CLUSTER_AXIS, cluster, 0L, 0L) / (double) PPM * TWO_PI;
            axisX[cluster] = StrictMath.cos(angle);
            axisY[cluster] = StrictMath.sin(angle);
            elongation[cluster] = centeredUnit(random, CLUSTER_ELONGATION, cluster, 0L, 0L)
                    * MAX_CLUSTER_ELONGATION;
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
                double distance = StrictMath.hypot(along / (1d + stretch), across / (1d - stretch));
                if (distance < bestDistance) {
                    secondDistance = bestDistance;
                    bestDistance = distance;
                } else if (distance < secondDistance) {
                    secondDistance = distance;
                }
            }
            double normalizedDistance = bestDistance / Math.max(1d, graph.spacingCells());
            double broadNoise = smoothNoise(
                    random, GEOGRAPHY, graph.x()[site], graph.y()[site], geographyScale);
            double geographicScore = -normalizedDistance + broadNoise * GEOGRAPHY_NOISE_AMPLITUDE;
            double separator = 0d;
            if (seeds.length > 1 && Double.isFinite(secondDistance)) {
                double gap = Math.max(0d, secondDistance - bestDistance);
                double normalizedGap = gap / Math.max(1d, graph.spacingCells());
                separator = fragmentation * StrictMath.exp(-normalizedGap / 0.72d) * SEPARATOR_STRENGTH;
            }
            score[site] = geographicScore - separator;
            separationPenalty[site] = separator;
        }
        return new GeographicForcing(score, separationPenalty);
    }

    private static boolean[] initializePhase(
            ControlGraph graph, GeographicForcing forcing, int[] seeds, int desiredSites) {
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
            for (int i = 0; i < neighbors.length; i++) {
                int neighbor = neighbors[i];
                double value = !graph.forcedOcean()[neighbor] && phase[neighbor] ? 1d : 0d;
                neighborPhase += value * weights[i];
            }
            double self = phase[site] ? 1d : 0d;
            double diffused = PHASE_SELF_WEIGHT * self + (1d - PHASE_SELF_WEIGHT) * neighborPhase;
            double forcingValue = normalizedForcing(forcing.score()[site]);
            rankingScore[site] = DIFFUSION_WEIGHT * diffused
                    + FORCING_WEIGHT * forcingValue
                    - forcing.separationPenalty()[site] * 0.10d;
        }
        protectSeeds(rankingScore, seeds);
        return selectVolume(graph, rankingScore, desiredSites);
    }

    private static boolean[] selectVolume(ControlGraph graph, double[] score, int desiredSites) {
        RankedSite[] ranked = new RankedSite[graph.interiorCount()];
        int count = 0;
        for (int site = 0; site < graph.x().length; site++) {
            if (!graph.forcedOcean()[site]) ranked[count++] = new RankedSite(site, score[site]);
        }
        Arrays.sort(ranked, Comparator
                .comparingDouble(RankedSite::score)
                .reversed()
                .thenComparingInt(RankedSite::site));
        boolean[] phase = new boolean[graph.x().length];
        int selected = Math.min(desiredSites, ranked.length);
        for (int rank = 0; rank < selected; rank++) {
            if (!Double.isFinite(ranked[rank].score())) {
                throw new IllegalStateException("V14 phase left an interior control site unreachable");
            }
            phase[ranked[rank].site()] = true;
        }
        return phase;
    }

    private static void protectSeeds(double[] score, int[] seeds) {
        for (int i = 0; i < seeds.length; i++) score[seeds[i]] = 10d - i * 1.0e-9d;
    }

    private static double normalizedForcing(double score) {
        return !Double.isFinite(score) ? 0d : 1d / (1d + StrictMath.exp(-score));
    }

    private static double rawCoastScoreAt(
            LegacyV15Random random,
            ControlGraph graph,
            boolean[] phase,
            int guaranteedMargin,
            int width,
            int height,
            int localX,
            int localY) {
        int edgeDistance = edgeDistance(localX, localY, width, height);
        double deformationCoordinate = (edgeDistance - guaranteedMargin)
                / (double) Math.max(1, graph.spacingCells());
        double deformationFactor = smooth(clamp01(deformationCoordinate));
        double spacing = graph.spacingCells();
        double warpScale = Math.max(3d, spacing * COAST_WARP_SCALE_SPACING_PPM / PPM);
        double detailScale = Math.max(3d, spacing * COAST_DETAIL_SCALE_SPACING_PPM / PPM);
        double warpAmplitude = spacing * COAST_WARP_AMPLITUDE_SPACING_PPM / PPM * WARP_ATTENUATION;
        double detailAmplitude = spacing * COAST_DETAIL_AMPLITUDE_SPACING_PPM / PPM * DETAIL_ATTENUATION;
        double px = localX + smoothNoise(random, WARP_X, localX, localY, warpScale)
                * warpAmplitude * deformationFactor;
        double py = localY + smoothNoise(random, WARP_Y, localX, localY, warpScale)
                * warpAmplitude * deformationFactor;
        int approximateGx = (int) StrictMath.floor(px / spacing) + 2;
        int approximateGy = (int) StrictMath.floor(py / spacing) + 2;
        double kernelRadius = Math.max(3d, spacing * KERNEL_RADIUS_IN_SPACING);
        int latticeRadius = Math.max(2, (int) StrictMath.ceil(kernelRadius / spacing) + 1);
        double signed = 0d;
        double totalWeight = 0d;
        for (int gy = approximateGy - latticeRadius; gy <= approximateGy + latticeRadius; gy++) {
            if (gy < 0 || gy >= graph.rows()) continue;
            for (int gx = approximateGx - latticeRadius; gx <= approximateGx + latticeRadius; gx++) {
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
        double boundaryRadius = Math.max(2d, spacing * 1.15d);
        double boundaryDistance = Math.max(0d, edgeDistance);
        if (boundaryDistance < boundaryRadius) {
            double weight = wendlandC2(boundaryDistance / boundaryRadius)
                    * EXTERNAL_OCEAN_KERNEL_WEIGHT;
            signed -= weight;
            totalWeight += weight;
        }
        double implicit = totalWeight > 0d ? signed / totalWeight : -1d;
        double detail = smoothNoise(random, COAST_DETAIL, localX, localY, detailScale)
                * detailAmplitude * deformationFactor;
        double coastScore = implicit * spacing + detail;
        if (edgeDistance < guaranteedMargin && coastScore >= 0d) {
            throw new IllegalStateException("V14 land phase breached the external-ocean boundary");
        }
        return coastScore;
    }

    private static double smoothNoise(
            LegacyV15Random random,
            String purpose,
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

    private static int randomPpm(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            long ordinal) {
        int sample = (int) ((random.sampleElevation(purpose, x, y, ordinal) >>> 48) & SAMPLE_MAX);
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    private static double centeredUnit(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, purpose, x, y, ordinal) / (double) PPM * 2d - 1d;
    }

    private static double wendlandC2(double normalizedDistance) {
        if (!(normalizedDistance >= 0d) || normalizedDistance >= 1d) return 0d;
        double oneMinus = 1d - normalizedDistance;
        double square = oneMinus * oneMinus;
        return square * square * (4d * normalizedDistance + 1d);
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static boolean contains(int[] values, int length, int target) {
        for (int i = 0; i < length; i++) if (values[i] == target) return true;
        return false;
    }

    private static long scaledCount(long area, int ppm) {
        long whole = area / PPM;
        long remainder = area % PPM;
        return Math.addExact(
                Math.multiplyExact(whole, ppm),
                (Math.multiplyExact(remainder, ppm) + PPM / 2L) / PPM);
    }

    private static final class RelaxedRowCache {
        private static final int MAX_ROWS = 24;
        private final LegacyV15Random random;
        private final ControlGraph graph;
        private final boolean[] phase;
        private final int guaranteedMargin;
        private final int width;
        private final int height;
        private final Map<RowKey, double[]> cache = new LinkedHashMap<>(32, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<RowKey, double[]> eldest) {
                return size() > MAX_ROWS;
            }
        };

        private RelaxedRowCache(
                LegacyV15Random random,
                ControlGraph graph,
                boolean[] phase,
                int guaranteedMargin,
                ContinuumWorldDomain domain) {
            this.random = random;
            this.graph = graph;
            this.phase = phase;
            this.guaranteedMargin = guaranteedMargin;
            this.width = Math.toIntExact(domain.width());
            this.height = Math.toIntExact(domain.height());
        }

        private synchronized double[] row(int pass, int y) {
            if (y < 0 || y >= height) throw new IllegalArgumentException("row outside V14 domain");
            RowKey key = new RowKey(pass, y);
            double[] existing = cache.get(key);
            if (existing != null) return existing;
            double[] created = pass == 0 ? rawRow(y) : relaxedRow(pass, y);
            cache.put(key, created);
            return created;
        }

        private double[] rawRow(int y) {
            double[] result = new double[width];
            for (int x = 0; x < width; x++) {
                result[x] = rawCoastScoreAt(
                        random, graph, phase, guaranteedMargin, width, height, x, y);
            }
            return result;
        }

        private double[] relaxedRow(int pass, int y) {
            double[] centerRow = row(pass - 1, y);
            double[] above = y > 0 ? row(pass - 1, y - 1) : null;
            double[] below = y + 1 < height ? row(pass - 1, y + 1) : null;
            double[] result = centerRow.clone();
            double bandWidth = Math.max(
                    1.5d,
                    graph.spacingCells() * COAST_RELAXATION_BAND_WIDTH_SPACING_PPM / (double) PPM);
            double maximumShift = COAST_RELAXATION_MAX_SHIFT_PPM / (double) PPM;
            for (int x = 0; x < width; x++) {
                if (edgeDistance(x, y, width, height) < guaranteedMargin) continue;
                double center = centerRow[x];
                if (!Double.isFinite(center) || StrictMath.abs(center) > bandWidth) continue;
                double weighted = center * COAST_RELAXATION_SELF_WEIGHT_PPM;
                long totalWeight = COAST_RELAXATION_SELF_WEIGHT_PPM;
                for (int oy = -1; oy <= 1; oy++) {
                    double[] source = oy < 0 ? above : oy > 0 ? below : centerRow;
                    if (source == null) continue;
                    for (int ox = -1; ox <= 1; ox++) {
                        if (ox == 0 && oy == 0) continue;
                        int nx = x + ox;
                        if (nx < 0 || nx >= width) continue;
                        double neighbor = source[nx];
                        if (!Double.isFinite(neighbor)) continue;
                        int weight = ox == 0 || oy == 0
                                ? COAST_RELAXATION_ORTHOGONAL_WEIGHT_PPM
                                : COAST_RELAXATION_DIAGONAL_WEIGHT_PPM;
                        weighted += neighbor * weight;
                        totalWeight += weight;
                    }
                }
                double target = weighted / totalWeight;
                double shift = Math.max(-maximumShift, Math.min(maximumShift, target - center));
                result[x] = center + shift;
            }
            return result;
        }
    }

    private record RowKey(int pass, int y) {}
    private record Preparation(double cutoff, double maximumInterior, long supportCellCount) {}
    private record InitialRadixScan(long positiveCount, double maximumPositive) {}
    private record RadixBucket(int bucket, long before, long count) {}
    private record CandidateNeighbor(int site, double distanceSquared) {}
    private record NeighborSet(int[] sites, double[] weights) {}
    private record GeographicForcing(double[] score, double[] separationPenalty) {}
    private record RankedSite(int site, double score) {}
    private record ControlGraph(
            int columns,
            int rows,
            int spacingCells,
            double[] x,
            double[] y,
            boolean[] forcedOcean,
            int[][] neighbors,
            double[][] neighborWeights,
            int interiorCount) {}
}
