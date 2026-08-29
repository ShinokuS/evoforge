package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.field.TerrainElevationField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed-budget large-domain execution of the accepted V15 inland-lowland selection vocabulary.
 *
 * <p>The historical finite algorithm globally smooths every dry cell, resolves a low-elevation
 * support threshold, regularizes connected support, then keeps at most six broad lowland components.
 * An unbounded Continuum domain cannot reproduce those global passes without O(area) startup. This
 * plan therefore samples a fixed real-coordinate lattice to resolve only the global support threshold
 * and candidate component envelopes. Final membership is <em>not</em> a scaled sampled mask: every
 * {@link #isLake} call re-evaluates the actual V12/V14 land elevation at that world coordinate and
 * uses the sampled component only as a bounded basin envelope.</p>
 *
 * <p>The calibration lattice is a fixed 64 x 64 maximum. Cheap land/interior tests run before the
 * substantially more expensive authored V12 elevation evaluation, so rejected samples never pay for
 * relief/coast evaluation. Component selection first requires the same world-coordinate center span
 * used by the earlier high-fidelity migration profile. Only when that strict pass finds no basin do
 * we account for each sample's represented bucket footprint. If the historical three-times support
 * budget is still disconnected solely at this coarse calibration resolution, progressively broader
 * lowland support thresholds are tried from the same real-coordinate samples. Reference-sized worlds
 * that resolve a basin at the historical threshold are therefore unchanged.</p>
 *
 * <p>The exact {@code V15InlandLakeDomainPlan} remains the finite-world oracle.</p>
 */
public final class V15ContinuumLakeDomainPlan {
    private static final int PPM = 1_000_000;
    private static final int SAMPLE_SIDE = 64;
    private static final int MINIMUM_INTERIOR_POTENTIAL_PPM = 180_000;
    private static final int MAX_MEMBERSHIP_CACHE = 65_536;
    private static final int[] SUPPORT_MULTIPLIERS = {3, 4, 6, 8};

    private final ContinuumWorldDomain domain;
    private final V14ContinuumBaseTerrainPlan continental;
    private final List<LakeBody> bodies;
    private final long targetLakeCells;
    private final long maximumSourceElevationSubunits;
    private final Map<Long, Boolean> membershipCache = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > MAX_MEMBERSHIP_CACHE;
        }
    };

    private V15ContinuumLakeDomainPlan(
            ContinuumWorldDomain domain,
            V14ContinuumBaseTerrainPlan continental,
            List<LakeBody> bodies,
            long targetLakeCells,
            long maximumSourceElevationSubunits) {
        this.domain = domain;
        this.continental = continental;
        this.bodies = List.copyOf(bodies);
        this.targetLakeCells = targetLakeCells;
        this.maximumSourceElevationSubunits = maximumSourceElevationSubunits;
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
        long targetLakeCells = multiplyPpm(dryLandCells, recipe.targetDryLandCoveragePpm());
        long maximumSourceElevation = Math.max(
                1L,
                (long) Math.max(1, maximumZCells)
                        * TerrainElevationField.SUBUNITS_PER_CELL
                        * recipe.maximumSourceElevationPpm()
                        / PPM);
        if (targetLakeCells <= 0L || dryLandCells <= 0L) {
            return new V15ContinuumLakeDomainPlan(
                    domain, continental, List.of(), Math.max(0L, targetLakeCells), maximumSourceElevation);
        }

        long limitingSpan = Math.min(domain.width(), domain.height());
        long minimumSpan = Math.max(
                recipe.minimumComponentSpanCells(),
                limitingSpan / recipe.componentSpanWorldDivisor());
        long minimumComponentCells = Math.max(4L, minimumSpan * minimumSpan / 2L);
        int maximumBodies = Math.min(
                recipe.maximumLakeBodies(),
                Math.max(1, Math.toIntExact(Math.min(
                        recipe.maximumLakeBodies(),
                        targetLakeCells / Math.max(1L, minimumComponentCells)))));

        int columns = Math.toIntExact(Math.min(SAMPLE_SIDE, domain.width()));
        int rows = Math.toIntExact(Math.min(SAMPLE_SIDE, domain.height()));
        int sampleCount = Math.multiplyExact(columns, rows);
        long nominalBucketX = Math.max(1L, divideCeil(domain.width(), columns));
        long nominalBucketY = Math.max(1L, divideCeil(domain.height(), rows));
        Sample[] samples = new Sample[sampleCount];
        int drySamples = 0;
        int eligibleSamples = 0;
        long[] eligibleElevations = new long[sampleCount];
        for (int sy = 0; sy < rows; sy++) {
            long y = sampledCoordinate(domain.height(), sy, rows);
            for (int sx = 0; sx < columns; sx++) {
                long x = sampledCoordinate(domain.width(), sx, columns);
                boolean dry = continental.landRank().isLand(x, y);
                if (dry) drySamples++;

                int interior = dry ? continental.landmass().potentialPpmAt(x, y) : 0;
                long elevation = 0L;
                boolean eligible = false;
                if (dry && interior >= MINIMUM_INTERIOR_POTENTIAL_PPM) {
                    elevation = continental.unrelaxedElevation().elevationSubunitsAt(x, y);
                    eligible = elevation > 0L && elevation <= maximumSourceElevation;
                }
                if (eligible) eligibleElevations[eligibleSamples++] = elevation;
                samples[sy * columns + sx] = new Sample(x, y, elevation, eligible);
            }
        }
        if (drySamples == 0 || eligibleSamples == 0) {
            return new V15ContinuumLakeDomainPlan(
                    domain, continental, List.of(), targetLakeCells, maximumSourceElevation);
        }

        double eligibleFractionOfDry = eligibleSamples / (double) drySamples;
        double estimatedEligibleCells = dryLandCells * eligibleFractionOfDry;
        long interiorCapacity = Math.max(
                0L,
                Math.round(estimatedEligibleCells * recipe.maximumInteriorOccupancyPpm() / PPM));
        long desiredLakeCells = Math.min(targetLakeCells, interiorCapacity);
        if (desiredLakeCells < minimumComponentCells) {
            return new V15ContinuumLakeDomainPlan(
                    domain, continental, List.of(), targetLakeCells, maximumSourceElevation);
        }

        Arrays.sort(eligibleElevations, 0, eligibleSamples);
        double representedCellsPerSample = domain.width() * (double) domain.height() / sampleCount;
        ComponentSelection selection = resolveComponents(
                samples,
                eligibleElevations,
                eligibleSamples,
                columns,
                rows,
                representedCellsPerSample,
                nominalBucketX,
                nominalBucketY,
                estimatedEligibleCells,
                desiredLakeCells,
                minimumSpan,
                minimumComponentCells);
        if (selection == null) {
            return new V15ContinuumLakeDomainPlan(
                    domain, continental, List.of(), targetLakeCells, maximumSourceElevation);
        }

        List<Component> components = selection.components();
        components.sort(Comparator
                .comparingDouble(Component::meanElevation)
                .thenComparing(Comparator.comparingDouble(Component::estimatedCells).reversed())
                .thenComparingLong(Component::minimumCell));

        List<Component> selected = new ArrayList<>();
        double selectedCells = 0d;
        for (Component component : components) {
            if (selected.size() >= maximumBodies) break;
            if (selectedCells >= desiredLakeCells && !selected.isEmpty()) break;
            selected.add(component);
            selectedCells += component.estimatedCells();
        }
        if (selected.isEmpty()) selected.add(components.get(0));

        List<LakeBody> bodies = new ArrayList<>(selected.size());
        for (Component component : selected) {
            long marginX = Math.max(1L, nominalBucketX / 2L);
            long marginY = Math.max(1L, nominalBucketY / 2L);
            long minX = Math.max(0L, component.minX() - marginX);
            long maxX = Math.min(domain.width() - 1L, component.maxX() + marginX);
            long minY = Math.max(0L, component.minY() - marginY);
            long maxY = Math.min(domain.height() - 1L, component.maxY() + marginY);
            double centerX = (minX + maxX) * 0.5;
            double centerY = (minY + maxY) * 0.5;
            double radiusX = Math.max(minimumSpan / 2.0, (maxX - minX + 1L) * 0.56);
            double radiusY = Math.max(minimumSpan / 2.0, (maxY - minY + 1L) * 0.56);
            bodies.add(new LakeBody(
                    minX,
                    maxX,
                    minY,
                    maxY,
                    centerX,
                    centerY,
                    radiusX,
                    radiusY,
                    Math.min(selection.supportThreshold(), component.maximumElevation())));
        }
        return new V15ContinuumLakeDomainPlan(
                domain,
                continental,
                bodies,
                targetLakeCells,
                maximumSourceElevation);
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
        if (bodies.isEmpty()) return false;
        long key = Math.addExact(Math.multiplyExact(y, domain.width()), x);
        synchronized (membershipCache) {
            Boolean cached = membershipCache.get(key);
            if (cached != null) return cached;
        }
        long elevation = continental.unrelaxedElevation().elevationSubunitsAt(x, y);
        boolean lake = computeMembership(x, y, elevation);
        synchronized (membershipCache) {
            membershipCache.put(key, lake);
        }
        return lake;
    }

    /** Uses caller-materialized unrelaxed V12 elevation and preserves the lake threshold layer. */
    public boolean isLake(long x, long y, long unrelaxedElevationSubunits) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside Continuum lake domain");
        }
        if (bodies.isEmpty()) return false;
        long key = Math.addExact(Math.multiplyExact(y, domain.width()), x);
        synchronized (membershipCache) {
            Boolean cached = membershipCache.get(key);
            if (cached != null) return cached;
        }
        boolean lake = computeMembership(x, y, unrelaxedElevationSubunits);
        synchronized (membershipCache) {
            membershipCache.put(key, lake);
        }
        return lake;
    }

    public double normalizedRadius(long x, long y) {
        double best = Double.POSITIVE_INFINITY;
        for (LakeBody body : bodies) {
            if (!body.inEnvelope(x, y)) continue;
            best = Math.min(best, body.normalizedRadius(x, y));
        }
        return best;
    }

    private boolean computeMembership(long x, long y, long elevation) {
        LakeBody candidate = null;
        for (LakeBody body : bodies) {
            if (!body.inEnvelope(x, y)) continue;
            if (body.normalizedRadius(x, y) > 1.18) continue;
            candidate = body;
            break;
        }
        if (candidate == null) return false;
        if (elevation <= 0L
                || elevation > maximumSourceElevationSubunits
                || elevation > candidate.maximumElevationSubunits()) {
            return false;
        }
        return continental.landmass().potentialPpmAt(x, y) >= MINIMUM_INTERIOR_POTENTIAL_PPM;
    }

    private static ComponentSelection resolveComponents(
            Sample[] samples,
            long[] eligibleElevations,
            int eligibleSamples,
            int width,
            int height,
            double representedCellsPerSample,
            long nominalBucketX,
            long nominalBucketY,
            double estimatedEligibleCells,
            long desiredLakeCells,
            long minimumSpan,
            long minimumComponentCells) {
        boolean[] support = new boolean[samples.length];
        for (int multiplier : SUPPORT_MULTIPLIERS) {
            double supportTarget = Math.min(
                    estimatedEligibleCells,
                    Math.max(desiredLakeCells, desiredLakeCells * (double) multiplier));
            double supportFraction = Math.min(1.0, supportTarget / Math.max(1.0, estimatedEligibleCells));
            int thresholdIndex = Math.max(
                    0,
                    Math.min(
                            eligibleSamples - 1,
                            (int) StrictMath.ceil(supportFraction * eligibleSamples) - 1));
            long supportThreshold = eligibleElevations[thresholdIndex];
            for (int index = 0; index < samples.length; index++) {
                Sample sample = samples[index];
                support[index] = sample.eligible() && sample.elevationSubunits() <= supportThreshold;
            }

            List<Component> components = collectComponents(
                    samples,
                    support,
                    width,
                    height,
                    representedCellsPerSample,
                    1L,
                    1L,
                    minimumSpan,
                    minimumComponentCells);
            if (components.isEmpty()) {
                components = collectComponents(
                        samples,
                        support,
                        width,
                        height,
                        representedCellsPerSample,
                        nominalBucketX,
                        nominalBucketY,
                        minimumSpan,
                        minimumComponentCells);
            }
            if (!components.isEmpty()) {
                return new ComponentSelection(supportThreshold, components);
            }
        }
        return null;
    }

    private static List<Component> collectComponents(
            Sample[] samples,
            boolean[] support,
            int width,
            int height,
            double representedCellsPerSample,
            long spanFootprintX,
            long spanFootprintY,
            long minimumSpan,
            long minimumComponentCells) {
        boolean[] visited = new boolean[support.length];
        int[] queue = new int[support.length];
        List<Component> result = new ArrayList<>();
        for (int start = 0; start < support.length; start++) {
            if (!support[start] || visited[start]) continue;
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            visited[start] = true;
            int count = 0;
            long elevationSum = 0L;
            long maximumElevation = 0L;
            long minimumCell = Long.MAX_VALUE;
            long minX = Long.MAX_VALUE;
            long maxX = Long.MIN_VALUE;
            long minY = Long.MAX_VALUE;
            long maxY = Long.MIN_VALUE;
            while (head < tail) {
                int cell = queue[head++];
                int sx = cell % width;
                int sy = cell / width;
                Sample sample = samples[cell];
                count++;
                elevationSum += sample.elevationSubunits();
                maximumElevation = Math.max(maximumElevation, sample.elevationSubunits());
                minimumCell = Math.min(minimumCell, cell);
                minX = Math.min(minX, sample.x());
                maxX = Math.max(maxX, sample.x());
                minY = Math.min(minY, sample.y());
                maxY = Math.max(maxY, sample.y());
                if (sx > 0) tail = enqueue(support, visited, queue, tail, cell - 1);
                if (sx + 1 < width) tail = enqueue(support, visited, queue, tail, cell + 1);
                if (sy > 0) tail = enqueue(support, visited, queue, tail, cell - width);
                if (sy + 1 < height) tail = enqueue(support, visited, queue, tail, cell + width);
            }
            long spanX = maxX - minX + spanFootprintX;
            long spanY = maxY - minY + spanFootprintY;
            double estimatedCells = count * representedCellsPerSample;
            if (spanX < minimumSpan
                    || spanY < minimumSpan
                    || estimatedCells < minimumComponentCells) {
                continue;
            }
            result.add(new Component(
                    count,
                    elevationSum,
                    maximumElevation,
                    minimumCell,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    estimatedCells));
        }
        return result;
    }

    private static int enqueue(
            boolean[] support,
            boolean[] visited,
            int[] queue,
            int tail,
            int cell) {
        if (!support[cell] || visited[cell]) return tail;
        visited[cell] = true;
        queue[tail] = cell;
        return tail + 1;
    }

    private static long sampledCoordinate(long extent, int bucket, int buckets) {
        if (buckets == extent) return bucket;
        long start = (long) bucket * extent / buckets;
        long endExclusive = (long) (bucket + 1) * extent / buckets;
        return Math.min(extent - 1L, start + Math.max(0L, (endExclusive - start - 1L) / 2L));
    }

    private static long divideCeil(long value, long divisor) {
        return Math.floorDiv(value - 1L, divisor) + 1L;
    }

    private static long multiplyPpm(long value, int ppm) {
        long quotient = value / PPM;
        long remainder = value % PPM;
        return quotient * ppm + remainder * ppm / PPM;
    }

    private record Sample(
            long x,
            long y,
            long elevationSubunits,
            boolean eligible) {}

    private record ComponentSelection(
            long supportThreshold,
            List<Component> components) {}

    private record Component(
            int samples,
            long elevationSum,
            long maximumElevation,
            long minimumCell,
            long minX,
            long maxX,
            long minY,
            long maxY,
            double estimatedCells) {
        double meanElevation() {
            return elevationSum / (double) samples;
        }
    }

    private record LakeBody(
            long minX,
            long maxX,
            long minY,
            long maxY,
            double centerX,
            double centerY,
            double radiusX,
            double radiusY,
            long maximumElevationSubunits) {
        boolean inEnvelope(long x, long y) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY;
        }

        double normalizedRadius(long x, long y) {
            return StrictMath.hypot((x - centerX) / radiusX, (y - centerY) / radiusY);
        }
    }
}
