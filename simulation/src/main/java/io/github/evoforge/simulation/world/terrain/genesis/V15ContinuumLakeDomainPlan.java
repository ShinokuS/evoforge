package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Fixed-budget large-domain approximation of the accepted V15 inland-lowland lake selection.
 *
 * <p>The finite historical algorithm globally smooths every dry cell and ranks connected lowland
 * components. That cannot have area-independent startup. This Continuum plan keeps the same V15
 * target coverage, source-height ceiling, scale-aware minimum span and maximum body count, but finds
 * body anchors from a fixed stratified set of real world coordinates. Footprints are deterministic
 * world-space lowland ellipses with bounded edge variation. No reduced terrain raster is generated.
 *
 * <p>This class is intentionally separate from {@link io.github.evoforge.simulation.world.terrain.field.V15InlandLakeDomainPlan};
 * the latter remains the exact finite-world oracle.</p>
 */
public final class V15ContinuumLakeDomainPlan {
    private static final int PPM = 1_000_000;
    private static final int SAMPLE_SIDE = 32;
    private static final int MINIMUM_INTERIOR_POTENTIAL_PPM = 180_000;
    private static final String SAMPLE = "world:v15-continuum-lake-sample";
    private static final String SHAPE = "world:v15-continuum-lake-shape";

    private final ContinuumWorldDomain domain;
    private final long seed;
    private final List<LakeBody> bodies;
    private final long targetLakeCells;

    private V15ContinuumLakeDomainPlan(
            ContinuumWorldDomain domain,
            long seed,
            List<LakeBody> bodies,
            long targetLakeCells) {
        this.domain = domain;
        this.seed = seed;
        this.bodies = List.copyOf(bodies);
        this.targetLakeCells = targetLakeCells;
    }

    public static V15ContinuumLakeDomainPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V14ContinuumBaseTerrainPlan continental,
            int maximumZCells) {
        if (domain == null || continental == null || !domain.equals(continental.domain())) {
            throw new IllegalArgumentException("Continuum lake inputs must share one domain");
        }
        V15InlandLakeDomainRecipe recipe = V15InlandLakeDomainRecipe.balanced();
        long dryLandCells = continental.landRank().landCount();
        long targetLakeCells = dryLandCells * recipe.targetDryLandCoveragePpm() / PPM;
        if (targetLakeCells <= 0L) {
            return new V15ContinuumLakeDomainPlan(domain, seed, List.of(), 0L);
        }

        int limitingSpan = Math.toIntExact(Math.min(domain.width(), domain.height()));
        int minimumSpan = Math.max(
                recipe.minimumComponentSpanCells(),
                limitingSpan / recipe.componentSpanWorldDivisor());
        long minimumComponentCells = Math.max(4L, (long) minimumSpan * minimumSpan / 2L);
        int maximumBodies = Math.min(
                recipe.maximumLakeBodies(),
                Math.max(1, Math.toIntExact(Math.min(
                        recipe.maximumLakeBodies(),
                        targetLakeCells / Math.max(1L, minimumComponentCells)))));
        if (maximumBodies <= 0) {
            return new V15ContinuumLakeDomainPlan(domain, seed, List.of(), targetLakeCells);
        }

        long maximumSourceElevation = Math.max(
                1L,
                (long) Math.max(1, maximumZCells)
                        * TerrainElevationField.SUBUNITS_PER_CELL
                        * recipe.maximumSourceElevationPpm()
                        / PPM);
        LegacyV15Random random = new LegacyV15Random(seed);
        List<Candidate> candidates = new ArrayList<>();
        int sampleColumns = Math.min(SAMPLE_SIDE, Math.toIntExact(domain.width()));
        int sampleRows = Math.min(SAMPLE_SIDE, Math.toIntExact(domain.height()));
        for (int sy = 0; sy < sampleRows; sy++) {
            long y = sampledCoordinate(random, sy, sampleRows, domain.height(), 1L);
            for (int sx = 0; sx < sampleColumns; sx++) {
                long x = sampledCoordinate(
                        random,
                        sx,
                        sampleColumns,
                        domain.width(),
                        ((long) sy << 32) ^ 0x71a3L);
                if (!continental.landRank().isLand(x, y)) continue;
                int interior = continental.landmass().potentialPpmAt(x, y);
                if (interior < MINIMUM_INTERIOR_POTENTIAL_PPM) continue;
                long elevation = continental.unrelaxedElevation().elevationSubunitsAt(x, y);
                if (elevation <= 0L || elevation > maximumSourceElevation) continue;
                long score = elevation * 4L
                        - (long) interior * TerrainElevationField.SUBUNITS_PER_CELL / PPM;
                candidates.add(new Candidate(x, y, score));
            }
        }
        if (candidates.isEmpty()) {
            return new V15ContinuumLakeDomainPlan(domain, seed, List.of(), targetLakeCells);
        }
        candidates.sort(Comparator
                .comparingLong(Candidate::score)
                .thenComparingLong(Candidate::y)
                .thenComparingLong(Candidate::x));

        int bodyCount = Math.min(maximumBodies, candidates.size());
        double nominalArea = targetLakeCells / (double) bodyCount;
        double nominalRadius = Math.max(minimumSpan / 2.0, StrictMath.sqrt(nominalArea / StrictMath.PI));
        List<LakeBody> bodies = new ArrayList<>(bodyCount);
        double minimumSeparation = nominalRadius * 2.4;
        double minimumSeparationSquared = minimumSeparation * minimumSeparation;
        for (Candidate candidate : candidates) {
            if (bodies.size() >= bodyCount) break;
            boolean tooClose = false;
            for (LakeBody existing : bodies) {
                double dx = candidate.x() - existing.centerX();
                double dy = candidate.y() - existing.centerY();
                if (dx * dx + dy * dy < minimumSeparationSquared) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) continue;
            bodies.add(createBody(random, candidate, nominalRadius, bodies.size()));
        }
        if (bodies.isEmpty()) {
            bodies.add(createBody(random, candidates.get(0), nominalRadius, 0));
        }
        return new V15ContinuumLakeDomainPlan(domain, seed, bodies, targetLakeCells);
    }

    public ContinuumWorldDomain domain() {
        return domain;
    }

    public int lakeBodyCount() {
        return bodies.size();
    }

    public long targetLakeCells() {
        return targetLakeCells;
    }

    public boolean isLake(long x, long y) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside Continuum lake domain");
        }
        for (LakeBody body : bodies) {
            if (body.contains(x, y, seed)) return true;
        }
        return false;
    }

    public double normalizedRadius(long x, long y) {
        double best = Double.POSITIVE_INFINITY;
        for (LakeBody body : bodies) {
            double radius = body.normalizedRadius(x, y);
            if (radius < best) best = radius;
        }
        return best;
    }

    private static LakeBody createBody(
            LegacyV15Random random,
            Candidate candidate,
            double nominalRadius,
            int ordinal) {
        int aspectSample = samplePpm(random, SHAPE, candidate.x(), candidate.y(), ordinal * 3L);
        double aspect = 0.72 + aspectSample / (double) PPM * 0.56;
        double radiusX = nominalRadius * StrictMath.sqrt(aspect);
        double radiusY = nominalRadius / StrictMath.sqrt(aspect);
        int angleSample = samplePpm(random, SHAPE, candidate.x(), candidate.y(), ordinal * 3L + 1L);
        double angle = angleSample / (double) PPM * StrictMath.PI;
        double edgePhase = samplePpm(random, SHAPE, candidate.x(), candidate.y(), ordinal * 3L + 2L)
                / (double) PPM * StrictMath.PI * 2.0;
        return new LakeBody(
                candidate.x(),
                candidate.y(),
                radiusX,
                radiusY,
                StrictMath.cos(angle),
                StrictMath.sin(angle),
                edgePhase);
    }

    private static long sampledCoordinate(
            LegacyV15Random random,
            int bucket,
            int buckets,
            long extent,
            long ordinal) {
        long start = bucket * extent / buckets;
        long endExclusive = (bucket + 1L) * extent / buckets;
        long span = Math.max(1L, endExclusive - start);
        int sample = samplePpm(random, SAMPLE, bucket, buckets, ordinal);
        long offset = Math.min(span - 1L, (long) sample * span / PPM);
        return Math.min(extent - 1L, start + offset);
    }

    private static int samplePpm(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            long ordinal) {
        int high = (int) ((random.sampleElevation(purpose, x, y, ordinal) >>> 48) & 0xffffL);
        return Math.toIntExact((long) high * PPM / 65_535L);
    }

    private record Candidate(long x, long y, long score) {}

    private record LakeBody(
            double centerX,
            double centerY,
            double radiusX,
            double radiusY,
            double axisX,
            double axisY,
            double edgePhase) {
        double normalizedRadius(long x, long y) {
            double dx = x - centerX;
            double dy = y - centerY;
            double along = dx * axisX + dy * axisY;
            double across = -dx * axisY + dy * axisX;
            return StrictMath.hypot(along / radiusX, across / radiusY);
        }

        boolean contains(long x, long y, long seed) {
            double radius = normalizedRadius(x, y);
            if (radius > 1.16) return false;
            if (radius < 0.84) return true;
            double angle = StrictMath.atan2(y - centerY, x - centerX);
            double modulation = 1.0
                    + 0.075 * StrictMath.sin(angle * 3.0 + edgePhase)
                    + 0.040 * StrictMath.sin(angle * 5.0 - edgePhase * 0.7);
            return radius <= modulation;
        }
    }
}
