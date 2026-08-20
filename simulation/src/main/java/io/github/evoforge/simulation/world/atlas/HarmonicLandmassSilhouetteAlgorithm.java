package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Geometric V14 landmass synthesis from deformed radial bodies rather than a rectangular-domain
 * noise threshold.
 *
 * <p>Primary bodies are oriented ellipses whose broad coastline is deformed by low-frequency
 * angular harmonics. Satellite bodies provide islands and detached coastal fragments. The only use
 * of rectangular world-edge distance is a hard exclusion guard; it never contributes a falloff or
 * coastline score. Maximum-land coverage is a capacity, not a quota: a deterministic radius
 * calibration grows the bodies until their real zero-level union approaches that capacity without
 * ever selecting low-score tails just to fill an arbitrary cell count.</p>
 */
final class HarmonicLandmassSilhouetteAlgorithm implements LandmassSilhouetteAlgorithm {
    static final HarmonicLandmassSilhouetteAlgorithm INSTANCE = new HarmonicLandmassSilhouetteAlgorithm();

    private static final GenerationPurposeId BODY = GenerationPurposeId.of("world:v14-landmass-body");
    private static final GenerationPurposeId COAST = GenerationPurposeId.of("world:v14-landmass-coast");
    private static final GenerationPurposeId SATELLITE = GenerationPurposeId.of("world:v14-landmass-satellite");
    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;
    private static final int MINIMUM_RADIUS_SCALE_PPM = 100_000;
    private static final int MAXIMUM_RADIUS_SCALE_PPM = 4_000_000;
    private static final int RADIUS_SEARCH_STEPS = 23;
    private static final double TWO_PI = StrictMath.PI * 2d;

    private HarmonicLandmassSilhouetteAlgorithm() {
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
        int margin = boundary.minimumOceanMarginCells();
        int candidateWidth = Math.max(0, width - margin * 2);
        int candidateHeight = Math.max(0, height - margin * 2);
        int candidateCount = Math.multiplyExact(candidateWidth, candidateHeight);
        if (boundary.maximumLandCells() > candidateCount) {
            throw new IllegalArgumentException("maximum land capacity must fit inside the hard ocean guard");
        }

        GenerationRandom random = GenerationRandom.from(genesis);
        List<Body> bodies = createBodies(random, width, height, calibration, recipe);
        GeometricDomain domain = precomputeDomainGeometry(
                bodies,
                width,
                height,
                margin,
                recipe.coast());
        int radiusScalePpm = calibrateRadiusScale(
                bodies,
                domain,
                boundary.maximumLandCells());
        return materializeSilhouette(
                bounds,
                bodies,
                domain,
                radiusScalePpm,
                boundary.maximumLandCells(),
                calibration.silhouetteInfluencePpm());
    }

    private static GeometricDomain precomputeDomainGeometry(
            List<Body> bodies,
            int width,
            int height,
            int margin,
            LandmassSilhouetteRecipe.CoastPolicy coast) {
        int area = Math.multiplyExact(width, height);
        boolean[] eligible = new boolean[area];
        double[] confinementPenalty = new double[area];
        double[][] radialRatioByBody = new double[bodies.size()][area];
        double centerX = (width - 1d) * 0.5d;
        double centerY = (height - 1d) * 0.5d;
        double halfWidth = Math.max(1d, width * 0.5d);
        double halfHeight = Math.max(1d, height * 0.5d);
        double confinementStart = coast.confinementStartPpm() / (double) PPM;
        double confinementStrength = coast.confinementStrengthPpm() / (double) PPM;

        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                if (edgeDistance(localX, localY, width, height) < margin) {
                    for (double[] ratios : radialRatioByBody) {
                        ratios[index] = Double.POSITIVE_INFINITY;
                    }
                    index++;
                    continue;
                }
                eligible[index] = true;
                double normalizedX = (localX - centerX) / halfWidth;
                double normalizedY = (localY - centerY) / halfHeight;
                double radial = StrictMath.hypot(normalizedX, normalizedY);
                if (radial > confinementStart) {
                    double tail = (radial - confinementStart)
                            / Math.max(0.000_001d, 1d - confinementStart);
                    confinementPenalty[index] = tail * tail * tail * confinementStrength;
                }
                for (int bodyIndex = 0; bodyIndex < bodies.size(); bodyIndex++) {
                    radialRatioByBody[bodyIndex][index] = bodies.get(bodyIndex)
                            .radialRatioAt(localX, localY);
                }
                index++;
            }
        }
        return new GeometricDomain(eligible, confinementPenalty, radialRatioByBody);
    }

    /** Finds the largest geometric radius scale whose true zero-level union fits the capacity. */
    private static int calibrateRadiusScale(
            List<Body> bodies,
            GeometricDomain domain,
            int maximumLandCells) {
        if (maximumLandCells <= 0) return MINIMUM_RADIUS_SCALE_PPM;
        int minimumCount = countPositiveSupport(bodies, domain, MINIMUM_RADIUS_SCALE_PPM);
        if (minimumCount > maximumLandCells) {
            throw new IllegalStateException("minimum geometric landmass already exceeds land capacity");
        }

        int maximumCount = countPositiveSupport(bodies, domain, MAXIMUM_RADIUS_SCALE_PPM);
        if (maximumCount <= maximumLandCells) return MAXIMUM_RADIUS_SCALE_PPM;

        int low = MINIMUM_RADIUS_SCALE_PPM;
        int high = MAXIMUM_RADIUS_SCALE_PPM;
        for (int step = 0; step < RADIUS_SEARCH_STEPS && low < high; step++) {
            int mid = low + (high - low + 1) / 2;
            int count = countPositiveSupport(bodies, domain, mid);
            if (count <= maximumLandCells) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    private static int countPositiveSupport(
            List<Body> bodies,
            GeometricDomain domain,
            int radiusScalePpm) {
        double radiusScale = radiusScalePpm / (double) PPM;
        int count = 0;
        for (int index = 0; index < domain.eligible().length; index++) {
            if (!domain.eligible()[index]) continue;
            if (geometricScore(bodies, domain, index, radiusScale) > 0d) count++;
        }
        return count;
    }

    private static LandmassSilhouette materializeSilhouette(
            WorldBounds bounds,
            List<Body> bodies,
            GeometricDomain domain,
            int radiusScalePpm,
            int maximumLandCells,
            int influencePpm) {
        double radiusScale = radiusScalePpm / (double) PPM;
        boolean[] support = new boolean[domain.eligible().length];
        int[] potentialPpm = new int[domain.eligible().length];
        double[] positiveScore = new double[domain.eligible().length];
        double maximumScore = 0d;
        int supportCount = 0;

        for (int index = 0; index < support.length; index++) {
            if (!domain.eligible()[index]) continue;
            double score = geometricScore(bodies, domain, index, radiusScale);
            if (!(score > 0d)) continue;
            support[index] = true;
            positiveScore[index] = score;
            maximumScore = Math.max(maximumScore, score);
            supportCount++;
        }
        if (supportCount > maximumLandCells) {
            throw new IllegalStateException("calibrated geometric support exceeded land capacity");
        }
        if (supportCount == 0 || !(maximumScore > 0d)) {
            throw new IllegalStateException("geometric landmass calibration produced no terrestrial support");
        }

        for (int index = 0; index < support.length; index++) {
            if (!support[index]) continue;
            potentialPpm[index] = (int) Math.min(
                    (long) PPM,
                    Math.max(1L, StrictMath.round(positiveScore[index] * PPM / maximumScore)));
        }
        return new LandmassSilhouette(
                bounds,
                support,
                potentialPpm,
                supportCount,
                influencePpm);
    }

    private static double geometricScore(
            List<Body> bodies,
            GeometricDomain domain,
            int index,
            double radiusScale) {
        double best = -Double.MAX_VALUE;
        for (int bodyIndex = 0; bodyIndex < bodies.size(); bodyIndex++) {
            Body body = bodies.get(bodyIndex);
            double score = body.peak()
                    - domain.radialRatioByBody()[bodyIndex][index] / radiusScale;
            best = Math.max(best, score);
        }
        return best - domain.confinementPenalty()[index];
    }

    private static List<Body> createBodies(
            GenerationRandom random,
            int width,
            int height,
            LandmassSilhouetteCalibration calibration,
            LandmassSilhouetteRecipe recipe) {
        int primaryCount = calibration.primaryBodyCount();
        int radius = calibration.primaryRadiusCells();
        int limitingSpan = Math.min(width, height);
        double centerX = (width - 1d) * 0.5d;
        double centerY = (height - 1d) * 0.5d;
        LandmassSilhouetteRecipe.BodyPolicy bodyPolicy = recipe.bodies();
        LandmassSilhouetteRecipe.CoastPolicy coast = recipe.coast();
        List<Body> result = new ArrayList<>(primaryCount + calibration.satelliteBodyCount());
        List<Body> primary = new ArrayList<>(primaryCount);

        double phase = unit(random, BODY, 0L, 0L, 0L) * TWO_PI;
        double anchorOffset = primaryCount == 1
                ? limitingSpan * 0.03d
                : limitingSpan * bodyPolicy.multiBodyAnchorOffsetWorldPpm() / PPM;

        for (int bodyId = 0; bodyId < primaryCount; bodyId++) {
            double sector = phase + TWO_PI * bodyId / primaryCount;
            double sectorJitter = centeredUnit(random, BODY, bodyId, 0L, 1L) * 0.15d;
            double anchorAngle = sector + sectorJitter;
            double jitterX = centeredUnit(random, BODY, bodyId, 0L, 2L) * width * 0.02d;
            double jitterY = centeredUnit(random, BODY, bodyId, 0L, 3L) * height * 0.02d;
            double cx = centerX + StrictMath.cos(anchorAngle) * anchorOffset + jitterX;
            double cy = centerY + StrictMath.sin(anchorAngle) * anchorOffset + jitterY;
            double rotation = unit(random, BODY, bodyId, 0L, 4L) * TWO_PI;
            double aspect = interpolate(
                    coast.minimumAspectPpm() / (double) PPM,
                    coast.maximumAspectPpm() / (double) PPM,
                    unit(random, BODY, bodyId, 0L, 5L));
            double aspectRoot = StrictMath.sqrt(aspect);
            double radiusVariation = 0.88d + unit(random, BODY, bodyId, 0L, 6L) * 0.24d;
            double radiusX = radius * radiusVariation * aspectRoot;
            double radiusY = radius * radiusVariation / aspectRoot;
            double[] amplitudes = new double[coast.harmonicCount()];
            double[] phases = new double[coast.harmonicCount()];
            double irregularity = calibration.irregularityPpm() / (double) PPM;
            for (int harmonic = 0; harmonic < coast.harmonicCount(); harmonic++) {
                int order = harmonic + 2;
                double decay = order <= 4 ? 0.52d : 0.28d;
                amplitudes[harmonic] = centeredUnit(random, COAST, bodyId, order, 0L)
                        * irregularity * decay;
                phases[harmonic] = unit(random, COAST, bodyId, order, 1L) * TWO_PI;
            }
            Body body = new Body(
                    cx,
                    cy,
                    radiusX,
                    radiusY,
                    rotation,
                    1d,
                    amplitudes,
                    phases);
            primary.add(body);
            result.add(body);
        }

        for (int satelliteId = 0; satelliteId < calibration.satelliteBodyCount(); satelliteId++) {
            int parentIndex = Math.min(
                    primary.size() - 1,
                    (int) ((long) randomPpm(random, SATELLITE, satelliteId, 0L, 0L)
                            * primary.size() / PPM));
            Body parent = primary.get(parentIndex);
            double angle = unit(random, SATELLITE, satelliteId, 0L, 1L) * TWO_PI;
            double reachPpm = bodyPolicy.satelliteMinimumReachPpm()
                    + (long) randomPpm(random, SATELLITE, satelliteId, 0L, 2L)
                    * bodyPolicy.satelliteReachRangePpm() / PPM;
            double reach = radius * reachPpm / PPM;
            double cx = parent.centerX() + StrictMath.cos(angle) * reach;
            double cy = parent.centerY() + StrictMath.sin(angle) * reach;
            double satelliteRadiusPpm = bodyPolicy.satelliteMinimumRadiusPpm()
                    + (long) randomPpm(random, SATELLITE, satelliteId, 0L, 3L)
                    * bodyPolicy.satelliteRadiusRangePpm() / PPM;
            double satelliteRadius = Math.max(2d, radius * satelliteRadiusPpm / PPM);
            double aspect = 1d + unit(random, SATELLITE, satelliteId, 0L, 4L) * 0.45d;
            double aspectRoot = StrictMath.sqrt(aspect);
            double peak = 0.91d + unit(random, SATELLITE, satelliteId, 0L, 5L) * 0.08d;
            result.add(new Body(
                    cx,
                    cy,
                    satelliteRadius * aspectRoot,
                    satelliteRadius / aspectRoot,
                    unit(random, SATELLITE, satelliteId, 0L, 6L) * TWO_PI,
                    peak,
                    new double[0],
                    new double[0]));
        }
        return List.copyOf(result);
    }

    private static int edgeDistance(int x, int y, int width, int height) {
        return Math.min(Math.min(x, width - 1 - x), Math.min(y, height - 1 - y));
    }

    private static double interpolate(double from, double to, double coordinate) {
        return from + (to - from) * coordinate;
    }

    private static double unit(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, purpose, x, y, ordinal) / (double) PPM;
    }

    private static double centeredUnit(
            GenerationRandom random,
            GenerationPurposeId purpose,
            long x,
            long y,
            long ordinal) {
        return unit(random, purpose, x, y, ordinal) * 2d - 1d;
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

    private record GeometricDomain(
            boolean[] eligible,
            double[] confinementPenalty,
            double[][] radialRatioByBody) {
        private GeometricDomain {
            if (eligible == null || confinementPenalty == null || radialRatioByBody == null
                    || eligible.length != confinementPenalty.length) {
                throw new IllegalArgumentException("geometric landmass domain arrays must be valid");
            }
            for (double[] ratios : radialRatioByBody) {
                if (ratios == null || ratios.length != eligible.length) {
                    throw new IllegalArgumentException("body geometry must match landmass domain");
                }
            }
        }
    }

    private record Body(
            double centerX,
            double centerY,
            double radiusX,
            double radiusY,
            double rotation,
            double peak,
            double[] amplitudes,
            double[] phases) {
        private Body {
            if (!(radiusX > 0d) || !(radiusY > 0d) || amplitudes.length != phases.length) {
                throw new IllegalArgumentException("landmass body geometry must be valid");
            }
            amplitudes = Arrays.copyOf(amplitudes, amplitudes.length);
            phases = Arrays.copyOf(phases, phases.length);
        }

        double radialRatioAt(double x, double y) {
            double dx = x - centerX;
            double dy = y - centerY;
            double cosine = StrictMath.cos(rotation);
            double sine = StrictMath.sin(rotation);
            double localX = dx * cosine + dy * sine;
            double localY = -dx * sine + dy * cosine;
            double normalizedX = localX / radiusX;
            double normalizedY = localY / radiusY;
            double angle = StrictMath.atan2(normalizedY, normalizedX);
            double radial = StrictMath.hypot(normalizedX, normalizedY);
            double boundaryRadius = 1d;
            for (int harmonic = 0; harmonic < amplitudes.length; harmonic++) {
                int order = harmonic + 2;
                boundaryRadius += amplitudes[harmonic]
                        * StrictMath.cos(order * angle + phases[harmonic]);
            }
            boundaryRadius = Math.max(0.55d, boundaryRadius);
            return radial / boundaryRadius;
        }
    }
}
