package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V14BathymetryRecipe;
import io.github.evoforge.simulation.world.terrain.genesis.V14ContinuumBathymetryCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V14ContinuumShorelineCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V15ContinuumLakeDomainPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V15InlandLakeBathymetryRecipe;

/**
 * Bounded large-domain V14/V15 water-depth execution.
 *
 * <p>Unit-resolution requests compute the accepted local shoreline chamfer and coastal-relief
 * character inside a fixed halo. When the shore lies outside that halo, a fixed-budget set of actual
 * sampled shoreline coordinates supplies only the missing global distance/max-distance facts. Ocean
 * depth then uses the historical V14 smoother-step baseline and causal coastal formulas rather than a
 * synthetic deep-ocean noise field. No finished low-resolution bathymetry raster is scaled.</p>
 */
public final class V15ContinuumBathymetryPageSource implements ContinuumScalarPageSource {
    private static final int PPM = 1_000_000;
    private static final int DISTANCE_SCALE = 1_000;
    private static final int CARDINAL_DISTANCE = 1_000;
    private static final int DIAGONAL_DISTANCE = 1_414;
    private static final int INFINITE_DISTANCE = Integer.MAX_VALUE / 4;
    private static final int MINIMUM_WATER_HALO = 48;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource base;
    private final V15ContinuumLakeDomainPlan lakes;
    private final V14ContinuumBathymetryCalibration calibration;
    private final V14BathymetryRecipe recipe;
    private final V15InlandLakeBathymetryRecipe lakeRecipe;
    private final V14ContinuumShorelineCalibration shoreline;
    private final int halo;

    public V15ContinuumBathymetryPageSource(
            ContinuumWorldDomain domain,
            long seed,
            ContinuumScalarPageSource base,
            V15ContinuumLakeDomainPlan lakes,
            V12LandRankPlan land,
            int minimumZCells) {
        if (domain == null || base == null || lakes == null || land == null
                || !domain.equals(base.domain())
                || !domain.equals(lakes.domain())) {
            throw new IllegalArgumentException("Continuum bathymetry dependencies must share one domain");
        }
        this.domain = domain;
        this.base = base;
        this.lakes = lakes;
        this.recipe = V14BathymetryRecipe.balanced();
        this.calibration = V14ContinuumBathymetryCalibration.compile(domain, minimumZCells, recipe);
        this.lakeRecipe = V15InlandLakeBathymetryRecipe.balanced();
        this.shoreline = V14ContinuumShorelineCalibration.prepare(domain, land);
        this.halo = Math.max(
                MINIMUM_WATER_HALO,
                calibration.coastalContextRadiusCells() * 3 + 2);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        if (window.step() != 1L) return materializeCoarse(window);

        long requestedMaxX = window.xAt(window.width() - 1);
        long requestedMaxY = window.yAt(window.height() - 1);
        long minX = Math.max(0L, window.minX() - halo);
        long minY = Math.max(0L, window.minY() - halo);
        long maxX = Math.min(domain.width() - 1L, requestedMaxX + halo);
        long maxY = Math.min(domain.height() - 1L, requestedMaxY + halo);
        int width = Math.toIntExact(maxX - minX + 1L);
        int height = Math.toIntExact(maxY - minY + 1L);
        ContinuumScalarPage localBase = base.materialize(
                new ContinuumSampleWindow(minX, minY, width, height, 1L));
        long[] elevation = new long[Math.multiplyExact(width, height)];
        int[] distance = new int[elevation.length];
        boolean hasLand = false;
        int cursor = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++, cursor++) {
                long value = Math.round(localBase.sample(x, y));
                elevation[cursor] = value;
                if (value > 0L) {
                    distance[cursor] = 0;
                    hasLand = true;
                } else {
                    distance[cursor] = INFINITE_DISTANCE;
                }
            }
        }
        if (hasLand) chamferDistance(distance, width, height);

        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long worldY = window.yAt(sampleY);
            int localY = Math.toIntExact(worldY - minY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++, cursor++) {
                long worldX = window.xAt(sampleX);
                int localX = Math.toIntExact(worldX - minX);
                int cell = localY * width + localX;
                long value = elevation[cell];
                if (value > 0L) {
                    output[cursor] = value;
                } else if (lakes.isLake(worldX, worldY)) {
                    output[cursor] = -lakeDepth(worldX, worldY);
                } else {
                    int localDistance = distance[cell];
                    int resolvedDistance = localDistance >= INFINITE_DISTANCE
                                    || localDistance > (halo - 2) * DISTANCE_SCALE
                            ? shoreline.distanceMilliAt(worldX, worldY)
                            : localDistance;
                    int coastalCharacter = localDistance >= INFINITE_DISTANCE
                            ? 0
                            : coastalReliefPpm(localX, localY, elevation, width, height);
                    output[cursor] = -oceanDepth(resolvedDistance, coastalCharacter);
                }
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private ContinuumScalarPage materializeCoarse(ContinuumSampleWindow window) {
        ContinuumScalarPage basePage = base.materialize(window);
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int y = 0; y < window.height(); y++) {
            long worldY = window.yAt(y);
            for (int x = 0; x < window.width(); x++, cursor++) {
                long worldX = window.xAt(x);
                long value = Math.round(basePage.sample(x, y));
                if (value > 0L) {
                    output[cursor] = value;
                } else if (lakes.isLake(worldX, worldY)) {
                    output[cursor] = -lakeDepth(worldX, worldY);
                } else {
                    output[cursor] = -baselineOceanDepth(shoreline.distanceMilliAt(worldX, worldY));
                }
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private long oceanDepth(int shorelineDistance, int coastalCharacterPpm) {
        int maximumDistance = shoreline.maximumShorelineDistance();
        long bodyDepthCap = bodyDepthCap(maximumDistance);
        long baselineDepth = baselineDepth(shorelineDistance, maximumDistance, bodyDepthCap);
        long coastalDepth = causalCoastalDepth(
                shorelineDistance,
                coastalCharacterPpm,
                baselineDepth,
                bodyDepthCap);
        return Math.max(1L, Math.min(bodyDepthCap, Math.max(baselineDepth, coastalDepth)));
    }

    private long baselineOceanDepth(int shorelineDistance) {
        int maximumDistance = shoreline.maximumShorelineDistance();
        long bodyDepthCap = bodyDepthCap(maximumDistance);
        return baselineDepth(shorelineDistance, maximumDistance, bodyDepthCap);
    }

    private long baselineDepth(int distance, int maximumDistance, long bodyDepthCap) {
        int coordinatePpm = (int) Math.min(PPM, (long) distance * PPM / Math.max(1, maximumDistance));
        int profilePpm = smootherStepPpm(coordinatePpm);
        return Math.max(1L, bodyDepthCap * profilePpm / PPM);
    }

    private long causalCoastalDepth(
            int shorelineDistance,
            int coastalCharacterPpm,
            long baselineDepth,
            long bodyDepthCap) {
        if (coastalCharacterPpm <= 0 || shorelineDistance <= 0) return 0L;
        int reliefCoordinatePpm = (int) Math.min(
                PPM,
                (long) coastalCharacterPpm * PPM / recipe.coastalReliefFullScalePpm());
        int reliefCharacterPpm = smootherStepPpm(reliefCoordinatePpm);
        long localFall = calibration.coastalMinimumFallSubunits()
                + (calibration.coastalMaximumFallSubunits()
                                - calibration.coastalMinimumFallSubunits())
                        * reliefCharacterPpm
                        / PPM;
        if (localFall <= 0L) return 0L;
        long geometricDepth = (long) shorelineDistance * localFall / DISTANCE_SCALE;
        int supportedSteps = Math.max(2, calibration.coastalContextRadiusCells() / 2);
        long requestedExtra = Math.multiplyExact(localFall, (long) supportedSteps);
        long maximumExtra = Math.min(bodyDepthCap, requestedExtra);
        long remainingDepth = Math.max(0L, bodyDepthCap - baselineDepth);
        long fadedExtra = bodyDepthCap <= 0L
                ? 0L
                : maximumExtra * remainingDepth / bodyDepthCap;
        long parallelDepth = Math.addExact(baselineDepth, fadedExtra);
        return Math.min(bodyDepthCap, Math.min(geometricDepth, parallelDepth));
    }

    private long bodyDepthCap(int maximumDistance) {
        long slopeSupported = Math.multiplyExact(
                (long) maximumDistance,
                calibration.maximumCardinalFallSubunits())
                / recipe.profileGradientBoundMilli();
        return Math.min(calibration.worldDepthCapSubunits(), Math.max(1L, slopeSupported));
    }

    private int coastalReliefPpm(
            int x,
            int y,
            long[] elevation,
            int width,
            int height) {
        int radius = calibration.coastalContextRadiusCells();
        int minX = Math.max(0, x - radius);
        int maxX = Math.min(width - 1, x + radius);
        int minY = Math.max(0, y - radius);
        int maxY = Math.min(height - 1, y + radius);
        long sum = 0L;
        int count = 0;
        for (int localY = minY; localY <= maxY; localY++) {
            int row = localY * width;
            for (int localX = minX; localX <= maxX; localX++) {
                long value = elevation[row + localX];
                if (value <= 0L) continue;
                sum += value;
                count++;
            }
        }
        if (count == 0) return 0;
        long average = sum / count;
        long horizontalReference = (long) radius * TerrainElevationField.SUBUNITS_PER_CELL;
        return (int) Math.min(
                recipe.coastalReliefFullScalePpm(),
                Math.max(0L, average * PPM / Math.max(1L, horizontalReference)));
    }

    private long lakeDepth(long x, long y) {
        double radius = lakes.normalizedRadius(x, y);
        if (!Double.isFinite(radius)) return TerrainElevationField.SUBUNITS_PER_CELL;
        double interior = Math.max(0.0, Math.min(1.0, 1.0 - radius));
        int profile = smootherStepPpm((int) Math.round(interior * PPM));
        long maximum = (long) lakeRecipe.maximumDepthCells() * TerrainElevationField.SUBUNITS_PER_CELL;
        long minimum = (long) lakeRecipe.minimumSignificantDepthCells()
                * TerrainElevationField.SUBUNITS_PER_CELL;
        long requested = minimum + (maximum - minimum) * profile / PPM;
        long verticalCapacity = Math.negateExact(calibration.floorSubunits());
        return Math.max(1L, Math.min(verticalCapacity, requested));
    }

    private static void chamferDistance(int[] distance, int width, int height) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                if (x > 0) best = Math.min(best, plus(distance[cell - 1], CARDINAL_DISTANCE));
                if (y > 0) best = Math.min(best, plus(distance[cell - width], CARDINAL_DISTANCE));
                if (x > 0 && y > 0) best = Math.min(best, plus(distance[cell - width - 1], DIAGONAL_DISTANCE));
                if (x + 1 < width && y > 0) best = Math.min(best, plus(distance[cell - width + 1], DIAGONAL_DISTANCE));
                distance[cell] = best;
            }
        }
        for (int y = height - 1; y >= 0; y--) {
            for (int x = width - 1; x >= 0; x--) {
                int cell = y * width + x;
                if (distance[cell] == 0) continue;
                int best = distance[cell];
                if (x + 1 < width) best = Math.min(best, plus(distance[cell + 1], CARDINAL_DISTANCE));
                if (y + 1 < height) best = Math.min(best, plus(distance[cell + width], CARDINAL_DISTANCE));
                if (x + 1 < width && y + 1 < height) best = Math.min(best, plus(distance[cell + width + 1], DIAGONAL_DISTANCE));
                if (x > 0 && y + 1 < height) best = Math.min(best, plus(distance[cell + width - 1], DIAGONAL_DISTANCE));
                distance[cell] = best;
            }
        }
    }

    private static int plus(int distance, int increment) {
        if (distance >= INFINITE_DISTANCE - increment) return INFINITE_DISTANCE;
        return distance + increment;
    }

    private static int smootherStepPpm(int coordinatePpm) {
        long t = Math.max(0, Math.min(PPM, coordinatePpm));
        long t2 = t * t / PPM;
        long t3 = t2 * t / PPM;
        long t4 = t3 * t / PPM;
        long t5 = t4 * t / PPM;
        return (int) Math.max(0L, Math.min((long) PPM, 6L * t5 - 15L * t4 + 10L * t3));
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maxX = window.xAt(window.width() - 1);
        long maxY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("window lies outside Continuum bathymetry domain");
        }
    }
}
