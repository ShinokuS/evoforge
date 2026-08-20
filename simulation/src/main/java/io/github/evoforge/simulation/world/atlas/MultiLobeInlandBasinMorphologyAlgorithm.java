package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationPurposeId;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Authors scale-aware asymmetric continental lowlands from overlapping broad lobes.
 *
 * <p>The visible basin is not a circle/ellipse template and no synthetic rim is raised. Candidate
 * regions are placed deep inside realized dry terrain. Several warped anisotropic lobes define only
 * the lowering support; an untouched outer fringe supplies the real terrain spill boundary. Ocean
 * bathymetry remains bit-identical and every dry cell remains at or above sea level.</p>
 */
final class MultiLobeInlandBasinMorphologyAlgorithm implements InlandBasinMorphologyAlgorithm {
    static final MultiLobeInlandBasinMorphologyAlgorithm INSTANCE =
            new MultiLobeInlandBasinMorphologyAlgorithm();

    private static final GenerationPurposeId CENTER_JITTER =
            GenerationPurposeId.of("world:v15-basin-center-jitter");
    private static final GenerationPurposeId LOBE_AXIS =
            GenerationPurposeId.of("world:v15-basin-lobe-axis");
    private static final GenerationPurposeId LOBE_OFFSET =
            GenerationPurposeId.of("world:v15-basin-lobe-offset");
    private static final GenerationPurposeId LOBE_RADIUS =
            GenerationPurposeId.of("world:v15-basin-lobe-radius");
    private static final GenerationPurposeId LOBE_ASPECT =
            GenerationPurposeId.of("world:v15-basin-lobe-aspect");
    private static final GenerationPurposeId WARP_X =
            GenerationPurposeId.of("world:v15-basin-warp-x");
    private static final GenerationPurposeId WARP_Y =
            GenerationPurposeId.of("world:v15-basin-warp-y");
    private static final GenerationPurposeId DEPTH =
            GenerationPurposeId.of("world:v15-basin-depth");
    private static final GenerationPurposeId FLOOR_VARIATION =
            GenerationPurposeId.of("world:v15-basin-floor-variation");

    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;
    private static final int LOBE_COUNT = 4;
    private static final double TWO_PI = StrictMath.PI * 2d;
    private static final double LOWERING_THRESHOLD = 0.18d;
    private static final double FRINGE_LOW = 0.035d;
    private static final double FRINGE_HIGH = 0.16d;
    private static final double MAX_WARP_RADIUS_FRACTION = 0.11d;
    private static final double SUPPORT_BOUND_RADIUS_FACTOR = 1.65d;

    private MultiLobeInlandBasinMorphologyAlgorithm() {
    }

    @Override
    public ElevationField generate(
            WorldGenesis genesis,
            ElevationField baseElevation,
            InlandBasinMorphologyCalibration calibration,
            InlandBasinMorphologyRecipe recipe) {
        if (genesis == null || baseElevation == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("inland basin morphology inputs must not be null");
        }
        WorldBounds bounds = baseElevation.bounds();
        if (!bounds.equals(genesis.spec().bounds())) {
            throw new IllegalArgumentException("inland basin terrain must match genesis bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        long[] elevations = new long[area];
        long minimumLandElevation = Long.MAX_VALUE;
        long maximumLandElevation = Long.MIN_VALUE;
        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                long elevation = baseElevation.elevationSubunitsAt(x, y);
                elevations[index++] = elevation;
                if (elevation >= 0L) {
                    minimumLandElevation = Math.min(minimumLandElevation, elevation);
                    maximumLandElevation = Math.max(maximumLandElevation, elevation);
                }
            }
        }
        if (calibration.targetBasinCount() == 0 || minimumLandElevation == Long.MAX_VALUE) {
            return new DenseElevationField(bounds, elevations);
        }

        int[] oceanDistance = cardinalDistanceToOceanOrBoundary(width, height, elevations);
        GenerationRandom random = GenerationRandom.from(genesis);
        int[] centerJitter = centerJitter(random, bounds, width, height);
        List<Candidate> selected = new ArrayList<>();

        for (int ordinal = 0; ordinal < calibration.targetBasinCount(); ordinal++) {
            Candidate candidate = bestCandidate(
                    width,
                    height,
                    elevations,
                    oceanDistance,
                    centerJitter,
                    minimumLandElevation,
                    maximumLandElevation,
                    selected,
                    calibration);
            if (candidate == null) break;
            BasinShape shape = basinShape(random, bounds, candidate, ordinal);
            long depth = basinDepth(random, candidate, ordinal, calibration);
            if (imprint(random, bounds, width, height, elevations, shape, depth, ordinal)) {
                selected.add(candidate);
            } else {
                break;
            }
        }

        return new DenseElevationField(bounds, elevations);
    }

    private static Candidate bestCandidate(
            int width,
            int height,
            long[] elevations,
            int[] oceanDistance,
            int[] centerJitter,
            long minimumLandElevation,
            long maximumLandElevation,
            List<Candidate> selected,
            InlandBasinMorphologyCalibration calibration) {
        Candidate best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        long elevationSpan = Math.max(1L, maximumLandElevation - minimumLandElevation);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                long elevation = elevations[index];
                if (elevation < 0L) continue;

                int available = Math.max(0, oceanDistance[index] - 2);
                int radius = Math.min(calibration.maximumRadiusCells(), available * 55 / 100);
                if (radius < calibration.minimumRadiusCells()) continue;
                if (!separated(x, y, radius, selected)) continue;

                double relativeElevation = (elevation - minimumLandElevation) / (double) elevationSpan;
                double lowlandPreference = 1d - Math.max(0d, Math.min(1d, relativeElevation));
                double jitter = centerJitter[index] / (double) PPM;
                double score = oceanDistance[index] * (0.90d + 0.20d * jitter)
                        + radius * lowlandPreference * 0.40d;
                if (score > bestScore
                        || score == bestScore && (best == null || index < best.index())) {
                    bestScore = score;
                    best = new Candidate(index, x, y, radius);
                }
            }
        }
        return best;
    }

    private static boolean separated(int x, int y, int radius, List<Candidate> selected) {
        for (Candidate previous : selected) {
            long dx = (long) x - previous.x();
            long dy = (long) y - previous.y();
            double minimum = (radius + previous.radius()) * 1.35d;
            if (dx * dx + dy * dy < minimum * minimum) return false;
        }
        return true;
    }

    private static BasinShape basinShape(
            GenerationRandom random,
            WorldBounds bounds,
            Candidate candidate,
            int ordinal) {
        Lobe[] lobes = new Lobe[LOBE_COUNT];
        for (int lobe = 0; lobe < LOBE_COUNT; lobe++) {
            long baseOrdinal = ordinal * 32L + lobe * 4L;
            double axis = unit(random, LOBE_AXIS, candidate.x(), candidate.y(), baseOrdinal) * TWO_PI;
            double offsetFraction = lobe == 0
                    ? 0d
                    : 0.18d + 0.34d * unit(
                            random, LOBE_OFFSET, candidate.x(), candidate.y(), baseOrdinal + 1L);
            double offsetAngle = axis + centeredUnit(
                    random, LOBE_OFFSET, candidate.x(), candidate.y(), baseOrdinal + 2L) * 0.9d;
            double centerX = candidate.x()
                    + StrictMath.cos(offsetAngle) * candidate.radius() * offsetFraction;
            double centerY = candidate.y()
                    + StrictMath.sin(offsetAngle) * candidate.radius() * offsetFraction;

            double radiusFraction = lobe == 0
                    ? 0.92d + 0.12d * unit(
                            random, LOBE_RADIUS, candidate.x(), candidate.y(), baseOrdinal)
                    : 0.48d + 0.30d * unit(
                            random, LOBE_RADIUS, candidate.x(), candidate.y(), baseOrdinal);
            double aspect = 0.62d + 0.34d * unit(
                    random, LOBE_ASPECT, candidate.x(), candidate.y(), baseOrdinal);
            double majorRadius = Math.max(2d, candidate.radius() * radiusFraction);
            double minorRadius = Math.max(2d, majorRadius * aspect);
            lobes[lobe] = new Lobe(
                    centerX,
                    centerY,
                    StrictMath.cos(axis),
                    StrictMath.sin(axis),
                    majorRadius,
                    minorRadius);
        }
        return new BasinShape(candidate, lobes, bounds);
    }

    private static long basinDepth(
            GenerationRandom random,
            Candidate candidate,
            int ordinal,
            InlandBasinMorphologyCalibration calibration) {
        double t = unit(random, DEPTH, candidate.x(), candidate.y(), ordinal);
        return calibration.minimumDepthSubunits()
                + Math.round((calibration.maximumDepthSubunits()
                        - calibration.minimumDepthSubunits()) * t);
    }

    private static boolean imprint(
            GenerationRandom random,
            WorldBounds bounds,
            int width,
            int height,
            long[] elevations,
            BasinShape shape,
            long depth,
            int ordinal) {
        Candidate candidate = shape.candidate();
        int boundRadius = (int) StrictMath.ceil(candidate.radius() * SUPPORT_BOUND_RADIUS_FACTOR);
        int minX = Math.max(0, candidate.x() - boundRadius);
        int maxX = Math.min(width - 1, candidate.x() + boundRadius);
        int minY = Math.max(0, candidate.y() - boundRadius);
        int maxY = Math.min(height - 1, candidate.y() + boundRadius);

        long rimMinimum = Long.MAX_VALUE;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * width + x;
                if (elevations[index] < 0L) continue;
                double support = supportAt(random, shape, x, y, ordinal);
                if (support >= FRINGE_LOW && support <= FRINGE_HIGH) {
                    rimMinimum = Math.min(rimMinimum, elevations[index]);
                }
            }
        }
        if (rimMinimum == Long.MAX_VALUE) return false;

        boolean changed = false;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int index = y * width + x;
                long current = elevations[index];
                if (current < 0L) continue;
                double support = supportAt(random, shape, x, y, ordinal);
                if (support <= LOWERING_THRESHOLD) continue;

                double normalized = smooth(clamp01(
                        (support - LOWERING_THRESHOLD) / (1d - LOWERING_THRESHOLD)));
                double floorNoise = smoothNoise(
                        random,
                        FLOOR_VARIATION,
                        bounds.minX() + x,
                        bounds.minY() + y,
                        Math.max(4d, candidate.radius() * 0.55d),
                        ordinal);
                double floorVariation = 0.94d + 0.06d * floorNoise;
                long localCut = Math.max(1L, Math.round(depth * normalized * floorVariation));
                long target = Math.max(0L, rimMinimum - localCut);
                long lowered = Math.min(current, target);
                if (lowered < current) {
                    elevations[index] = lowered;
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static double supportAt(
            GenerationRandom random,
            BasinShape shape,
            int localX,
            int localY,
            int ordinal) {
        Candidate candidate = shape.candidate();
        WorldBounds bounds = shape.bounds();
        double scale = Math.max(5d, candidate.radius() * 1.05d);
        double warpAmplitude = candidate.radius() * MAX_WARP_RADIUS_FRACTION;
        double worldX = bounds.minX() + localX;
        double worldY = bounds.minY() + localY;
        double x = localX + smoothNoise(random, WARP_X, worldX, worldY, scale, ordinal)
                * warpAmplitude;
        double y = localY + smoothNoise(random, WARP_Y, worldX, worldY, scale, ordinal)
                * warpAmplitude;

        double support = 0d;
        for (Lobe lobe : shape.lobes()) {
            double dx = x - lobe.centerX();
            double dy = y - lobe.centerY();
            double along = dx * lobe.axisX() + dy * lobe.axisY();
            double across = -dx * lobe.axisY() + dy * lobe.axisX();
            double distance = StrictMath.sqrt(
                    along * along / (lobe.majorRadius() * lobe.majorRadius())
                            + across * across / (lobe.minorRadius() * lobe.minorRadius()));
            if (distance >= 1d) continue;
            double oneMinus = 1d - distance;
            double kernel = oneMinus * oneMinus * (1d + 2d * distance);
            support = Math.max(support, kernel);
        }
        return clamp01(support);
    }

    private static int[] centerJitter(
            GenerationRandom random,
            WorldBounds bounds,
            int width,
            int height) {
        int[] jitter = new int[Math.multiplyExact(width, height)];
        int index = 0;
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                jitter[index++] = randomPpm(random, CENTER_JITTER, x, y, 0L);
            }
        }
        return jitter;
    }

    private static int[] cardinalDistanceToOceanOrBoundary(
            int width,
            int height,
            long[] elevations) {
        int[] distance = new int[elevations.length];
        Arrays.fill(distance, Integer.MAX_VALUE);
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = y * width + x;
                if (elevations[index] < 0L || x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    distance[index] = 0;
                    queue.addLast(index);
                }
            }
        }
        while (!queue.isEmpty()) {
            int cell = queue.removeFirst();
            int x = cell % width;
            int y = cell / width;
            int nextDistance = distance[cell] + 1;
            if (x > 0) visitDistance(cell - 1, nextDistance, distance, queue);
            if (x + 1 < width) visitDistance(cell + 1, nextDistance, distance, queue);
            if (y > 0) visitDistance(cell - width, nextDistance, distance, queue);
            if (y + 1 < height) visitDistance(cell + width, nextDistance, distance, queue);
        }
        return distance;
    }

    private static void visitDistance(
            int index,
            int candidate,
            int[] distance,
            ArrayDeque<Integer> queue) {
        if (candidate >= distance[index]) return;
        distance[index] = candidate;
        queue.addLast(index);
    }

    private static double smoothNoise(
            GenerationRandom random,
            GenerationPurposeId purpose,
            double x,
            double y,
            double scale,
            long ordinal) {
        double gx = x / scale;
        double gy = y / scale;
        long x0 = (long) StrictMath.floor(gx);
        long y0 = (long) StrictMath.floor(gy);
        double tx = smooth(gx - x0);
        double ty = smooth(gy - y0);
        double a = centeredUnit(random, purpose, x0, y0, ordinal);
        double b = centeredUnit(random, purpose, x0 + 1L, y0, ordinal);
        double c = centeredUnit(random, purpose, x0, y0 + 1L, ordinal);
        double d = centeredUnit(random, purpose, x0 + 1L, y0 + 1L, ordinal);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * ty;
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

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }

    private static double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private record Candidate(int index, int x, int y, int radius) {
    }

    private record Lobe(
            double centerX,
            double centerY,
            double axisX,
            double axisY,
            double majorRadius,
            double minorRadius) {
    }

    private record BasinShape(Candidate candidate, Lobe[] lobes, WorldBounds bounds) {
    }
}
