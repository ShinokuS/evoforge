package io.github.evoforge.simulation.world.geophysics;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Finite-world topology wrapper for an already accepted macro-geophysical model.
 *
 * <p>The wrapped model remains the sole author of macro elevation. This class samples that model on
 * a small irregular control graph, identifies only sampled source-land components that reach the
 * finite world boundary, and regularizes those components against an external-ocean guard band.
 * The resulting graph phase is reconstructed with the same compact-support Wendland C2 family that
 * was manually accepted for the V15 landmass silhouette. Existing source ocean and unaffected
 * interior land are returned bit-identically.</p>
 */
final class FiniteMacroGeophysicalField implements MacroGeophysicalModel {
    private static final int INTERIOR_LATTICE_SIDE = 65;
    private static final int GUARD_LAYERS = 2;
    private static final int REGULARIZATION_PASSES = 8;
    private static final int LOCAL_RADIUS = 3;

    private static final double SITE_JITTER = 0.27d;
    private static final double KERNEL_RADIUS_IN_SPACING = 2.70d;
    private static final double SELF_WEIGHT = 0.40d;
    private static final double DIFFUSION_WEIGHT = 0.58d;
    private static final double SOURCE_WEIGHT = 1.0d - DIFFUSION_WEIGHT;
    private static final double EDIT_INFLUENCE_THRESHOLD = 0.16d;
    private static final double EDGE_OCEAN_ELEVATION = -0.035d;

    private static final long JITTER_X_SALT = 0x4E6F727468536561L;
    private static final long JITTER_Y_SALT = 0x536F757468536561L;

    private final MacroGeophysicalModel source;
    private final long width;
    private final long height;
    private final long seed;
    private final long revision;
    private final double spacingX;
    private final double spacingY;
    private final int columns;
    private final int rows;
    private final double[] siteX;
    private final double[] siteY;
    private final boolean[] guardOcean;
    private final boolean[] sourceLand;
    private final boolean[] editable;
    private final double[] phase;

    FiniteMacroGeophysicalField(
            MacroGeophysicalModel source,
            long width,
            long height,
            long seed,
            long revision) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        if (width < 2L || height < 2L) {
            throw new IllegalArgumentException("finite world dimensions must be at least 2x2");
        }
        this.source = source;
        this.width = width;
        this.height = height;
        this.seed = seed;
        this.revision = revision;
        this.spacingX = (width - 1d) / (INTERIOR_LATTICE_SIDE - 1d);
        this.spacingY = (height - 1d) / (INTERIOR_LATTICE_SIDE - 1d);
        this.columns = INTERIOR_LATTICE_SIDE + GUARD_LAYERS * 2;
        this.rows = INTERIOR_LATTICE_SIDE + GUARD_LAYERS * 2;

        int count = Math.multiplyExact(columns, rows);
        this.siteX = new double[count];
        this.siteY = new double[count];
        this.guardOcean = new boolean[count];
        this.sourceLand = new boolean[count];
        this.editable = new boolean[count];
        this.phase = new double[count];

        buildControlGraph();
        markBoundaryConnectedSourceLand();
        regularizeEditablePhase();
    }

    @Override
    public double elevationAt(long x, long y) {
        double sourceElevation = source.elevationAt(x, y);
        if (!(sourceElevation > 0d)) return sourceElevation;

        if (x <= 0L || y <= 0L || x >= width - 1L || y >= height - 1L) {
            return EDGE_OCEAN_ELEVATION;
        }

        InterpolatedPhase reconstructed = interpolatePhase(x, y);
        if (reconstructed.editInfluence() < EDIT_INFLUENCE_THRESHOLD
                || reconstructed.signedPhase() >= 0d) {
            return sourceElevation;
        }

        // Only conflicting source land crosses the datum. The original positive elevation remains
        // untouched everywhere the regularized finite topology still accepts land.
        return Math.max(-1d, reconstructed.signedPhase() * 0.22d);
    }

    @Override
    public MacroGeophysicalStructure structureAt(long x, long y) {
        return source.structureAt(x, y);
    }

    private void buildControlGraph() {
        for (int gy = 0; gy < rows; gy++) {
            int latticeY = gy - GUARD_LAYERS;
            for (int gx = 0; gx < columns; gx++) {
                int latticeX = gx - GUARD_LAYERS;
                int index = index(gx, gy);

                double baseX = latticeX * spacingX;
                double baseY = latticeY * spacingY;
                double jitterX = signedUnit(hash(latticeX, latticeY, JITTER_X_SALT))
                        * spacingX * SITE_JITTER;
                double jitterY = signedUnit(hash(latticeX, latticeY, JITTER_Y_SALT))
                        * spacingY * SITE_JITTER;

                // Keep the four physical corner/edge lattice lines stable while irregularizing the
                // graph around them. The external guard itself remains irregular.
                boolean physicalX = latticeX == 0 || latticeX == INTERIOR_LATTICE_SIDE - 1;
                boolean physicalY = latticeY == 0 || latticeY == INTERIOR_LATTICE_SIDE - 1;
                siteX[index] = baseX + (physicalX ? 0d : jitterX);
                siteY[index] = baseY + (physicalY ? 0d : jitterY);

                boolean outside = latticeX < 0
                        || latticeY < 0
                        || latticeX >= INTERIOR_LATTICE_SIDE
                        || latticeY >= INTERIOR_LATTICE_SIDE;
                guardOcean[index] = outside;
                if (outside) {
                    sourceLand[index] = false;
                    phase[index] = -1d;
                    continue;
                }

                long sampleX = clampCoordinate(Math.round(siteX[index]), width);
                long sampleY = clampCoordinate(Math.round(siteY[index]), height);
                double elevation = source.elevationAt(sampleX, sampleY);
                sourceLand[index] = elevation > 0d;
                phase[index] = sourceLand[index] ? 1d : -1d;
            }
        }
    }

    private void markBoundaryConnectedSourceLand() {
        boolean[] visited = new boolean[sourceLand.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();

        for (int gy = GUARD_LAYERS; gy < rows - GUARD_LAYERS; gy++) {
            for (int gx = GUARD_LAYERS; gx < columns - GUARD_LAYERS; gx++) {
                int site = index(gx, gy);
                if (!sourceLand[site] || visited[site]) continue;

                int[] component = new int[sourceLand.length];
                int componentSize = 0;
                boolean touchesBoundary = false;
                visited[site] = true;
                queue.add(site);

                while (!queue.isEmpty()) {
                    int current = queue.removeFirst();
                    component[componentSize++] = current;
                    int cx = current % columns;
                    int cy = current / columns;
                    int latticeX = cx - GUARD_LAYERS;
                    int latticeY = cy - GUARD_LAYERS;
                    if (latticeX == 0
                            || latticeY == 0
                            || latticeX == INTERIOR_LATTICE_SIDE - 1
                            || latticeY == INTERIOR_LATTICE_SIDE - 1) {
                        touchesBoundary = true;
                    }

                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = cx + ox;
                            int ny = cy + oy;
                            if (nx < GUARD_LAYERS
                                    || ny < GUARD_LAYERS
                                    || nx >= columns - GUARD_LAYERS
                                    || ny >= rows - GUARD_LAYERS) {
                                continue;
                            }
                            int next = index(nx, ny);
                            if (sourceLand[next] && !visited[next]) {
                                visited[next] = true;
                                queue.addLast(next);
                            }
                        }
                    }
                }

                if (touchesBoundary) {
                    for (int componentIndex = 0; componentIndex < componentSize; componentIndex++) {
                        editable[component[componentIndex]] = true;
                    }
                }
            }
        }
    }

    private void regularizeEditablePhase() {
        double[] current = phase.clone();
        double[] next = new double[phase.length];

        for (int pass = 0; pass < REGULARIZATION_PASSES; pass++) {
            Arrays.fill(next, -1d);
            for (int gy = 0; gy < rows; gy++) {
                for (int gx = 0; gx < columns; gx++) {
                    int site = index(gx, gy);
                    if (guardOcean[site]) {
                        next[site] = -1d;
                        continue;
                    }
                    if (!editable[site]) {
                        next[site] = sourceLand[site] ? 1d : -1d;
                        continue;
                    }

                    double neighborSum = 0d;
                    double neighborWeight = 0d;
                    for (int oy = -1; oy <= 1; oy++) {
                        for (int ox = -1; ox <= 1; ox++) {
                            if (ox == 0 && oy == 0) continue;
                            int nx = gx + ox;
                            int ny = gy + oy;
                            if (nx < 0 || ny < 0 || nx >= columns || ny >= rows) continue;
                            int neighbor = index(nx, ny);
                            double dx = siteX[neighbor] - siteX[site];
                            double dy = siteY[neighbor] - siteY[site];
                            double distance = Math.hypot(dx / spacingX, dy / spacingY);
                            if (!(distance > 0d)) continue;
                            double weight = 1d / distance;
                            neighborSum += current[neighbor] * weight;
                            neighborWeight += weight;
                        }
                    }
                    double neighborPhase = neighborWeight > 0d
                            ? neighborSum / neighborWeight
                            : current[site];
                    double diffused = SELF_WEIGHT * current[site]
                            + (1d - SELF_WEIGHT) * neighborPhase;

                    long sampleX = clampCoordinate(Math.round(siteX[site]), width);
                    long sampleY = clampCoordinate(Math.round(siteY[site]), height);
                    double sourceElevation = source.elevationAt(sampleX, sampleY);
                    double sourcePreference = Math.tanh(sourceElevation * 4.5d);
                    next[site] = clamp(
                            DIFFUSION_WEIGHT * diffused + SOURCE_WEIGHT * sourcePreference,
                            -1d,
                            1d);
                }
            }
            double[] swap = current;
            current = next;
            next = swap;
        }

        System.arraycopy(current, 0, phase, 0, phase.length);
    }

    private InterpolatedPhase interpolatePhase(long x, long y) {
        int approximateX = (int) Math.round(x / spacingX) + GUARD_LAYERS;
        int approximateY = (int) Math.round(y / spacingY) + GUARD_LAYERS;
        double kernelRadiusX = spacingX * KERNEL_RADIUS_IN_SPACING;
        double kernelRadiusY = spacingY * KERNEL_RADIUS_IN_SPACING;

        double signed = 0d;
        double editableWeight = 0d;
        double totalWeight = 0d;
        for (int gy = approximateY - LOCAL_RADIUS; gy <= approximateY + LOCAL_RADIUS; gy++) {
            if (gy < 0 || gy >= rows) continue;
            for (int gx = approximateX - LOCAL_RADIUS; gx <= approximateX + LOCAL_RADIUS; gx++) {
                if (gx < 0 || gx >= columns) continue;
                int site = index(gx, gy);
                double dx = (x - siteX[site]) / kernelRadiusX;
                double dy = (y - siteY[site]) / kernelRadiusY;
                double normalizedDistance = Math.hypot(dx, dy);
                double weight = wendlandC2(normalizedDistance);
                if (!(weight > 0d)) continue;
                signed += phase[site] * weight;
                if (editable[site] || guardOcean[site]) editableWeight += weight;
                totalWeight += weight;
            }
        }

        if (!(totalWeight > 0d)) return new InterpolatedPhase(1d, 0d);
        return new InterpolatedPhase(signed / totalWeight, editableWeight / totalWeight);
    }

    private int index(int x, int y) {
        return y * columns + x;
    }

    private long hash(long x, long y, long salt) {
        long value = mix64(seed ^ salt ^ revision);
        value = mix64(value ^ mix64(x));
        return mix64(value ^ Long.rotateLeft(mix64(y), 29));
    }

    private static long clampCoordinate(long coordinate, long extent) {
        return Math.max(0L, Math.min(extent - 1L, coordinate));
    }

    private static double wendlandC2(double normalizedDistance) {
        if (!(normalizedDistance >= 0d) || normalizedDistance >= 1d) return 0d;
        double oneMinus = 1d - normalizedDistance;
        double square = oneMinus * oneMinus;
        return square * square * (4d * normalizedDistance + 1d);
    }

    private static double signedUnit(long hash) {
        return ((hash >>> 11) * 0x1.0p-53) * 2d - 1d;
    }

    private static long mix64(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private record InterpolatedPhase(double signedPhase, double editInfluence) {}
}
