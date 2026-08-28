package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV12Noise;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV15Random;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;
import io.github.evoforge.simulation.world.terrain.genesis.V15TerrainCoordinateFrame;

/**
 * Native-coordinate execution of the accepted V12 terrestrial height law for large V15 worlds.
 *
 * <p>The bounded V15 planning field is used only as the macro land/lake/water membership authority.
 * Dry-land height is rebuilt from the historical V12 coast baseline plus uplift, landforms, ridges
 * and rolling detail in the declared world's real cell coordinates. This is the key distinction
 * between scaling a continent and incorrectly scaling an already-finished terrain raster.</p>
 */
public final class V15NativeReliefPageSource implements ContinuumScalarPageSource {
    private static final int PPM = 1_000_000;
    private static final long MAX_DENSE_COAST_HALO_SAMPLES = 1_000_000L;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource membership;
    private final LegacyV15Random random;
    private final V15TerrainCoordinateFrame frame;
    private final V12TerrainCalibration calibration;
    private final V12TerrainRecipe recipe;
    private final long baseTerrainAmplitude;

    public V15NativeReliefPageSource(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            ContinuumScalarPageSource membership,
            int baseTerrainCeilingCells,
            int maximumZCells) {
        if (domain == null || definition == null || membership == null) {
            throw new IllegalArgumentException("native V15 relief inputs must not be null");
        }
        if (!domain.equals(membership.domain())) {
            throw new IllegalArgumentException("native V15 relief membership must match its domain");
        }
        if (baseTerrainCeilingCells <= 0 || maximumZCells < baseTerrainCeilingCells) {
            throw new IllegalArgumentException("V15 relief height bounds are inconsistent");
        }
        this.domain = domain;
        this.membership = membership;
        this.random = new LegacyV15Random(seed);
        this.frame = V15TerrainCoordinateFrame.centered(domain);
        this.recipe = V12TerrainRecipe.balanced();
        this.calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        this.baseTerrainAmplitude = Math.multiplyExact(
                (long) baseTerrainCeilingCells,
                TerrainElevationField.SUBUNITS_PER_CELL);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        ContinuumScalarPage membershipPage = membership.materialize(window);
        ContinuumScalarPage coastHalo = coastHalo(window);
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                long x = window.xAt(sampleX);
                long membershipHeight = Math.round(membershipPage.sample(sampleX, sampleY));
                if (membershipHeight <= 0L) {
                    output[cursor++] = membershipHeight;
                    continue;
                }

                int interiorityPpm = coastHalo != null
                        ? coastalInteriorityPpm(x, y, coastHalo)
                        : inferredInteriorityPpm(membershipHeight);
                long reliefPpm = reliefSignalPpm(frame.legacyX(x), frame.legacyY(y));
                int coastGatePpm = recipe.coastMinimumReliefGatePpm()
                        + (int) ((long) interiorityPpm
                                * (PPM - recipe.coastMinimumReliefGatePpm()) / PPM);
                reliefPpm = reliefPpm * coastGatePpm / PPM;

                long baseHeightPpm = recipe.coastBaseHeightPpm()
                        + (long) interiorityPpm * recipe.coastInteriorHeightPpm() / PPM;
                int heightPpm = LegacyV12Noise.clampPpm(baseHeightPpm + reliefPpm);
                output[cursor++] = positiveNormalizedHeight(heightPpm, baseTerrainAmplitude);
            }
        }
        return new ContinuumScalarPage(window, output);
    }

    private ContinuumScalarPage coastHalo(ContinuumSampleWindow window) {
        int transition = recipe.coastTransitionCells();
        long requestedMaxX = window.xAt(window.width() - 1);
        long requestedMaxY = window.yAt(window.height() - 1);
        long minimumX = Math.max(0L, window.minX() - transition);
        long minimumY = Math.max(0L, window.minY() - transition);
        long maximumX = Math.min(domain.width() - 1L, requestedMaxX + transition);
        long maximumY = Math.min(domain.height() - 1L, requestedMaxY + transition);
        long width = maximumX - minimumX + 1L;
        long height = maximumY - minimumY + 1L;
        if (width <= 0L || height <= 0L
                || width > Integer.MAX_VALUE
                || height > Integer.MAX_VALUE
                || width > MAX_DENSE_COAST_HALO_SAMPLES / height) {
            return null;
        }
        return membership.materialize(new ContinuumSampleWindow(
                minimumX,
                minimumY,
                Math.toIntExact(width),
                Math.toIntExact(height),
                1L));
    }

    private int coastalInteriorityPpm(long x, long y, ContinuumScalarPage halo) {
        int transition = recipe.coastTransitionCells();
        for (int distance = 1; distance <= transition; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dy = distance - Math.abs(dx);
                long nx = x + dx;
                long firstY = y + dy;
                if (domain.contains(nx, firstY) && !isLand(halo, nx, firstY)) {
                    return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                }
                if (dy != 0) {
                    long secondY = y - dy;
                    if (domain.contains(nx, secondY) && !isLand(halo, nx, secondY)) {
                        return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                    }
                }
            }
        }
        return PPM;
    }

    private static boolean isLand(ContinuumScalarPage halo, long x, long y) {
        int localX = Math.toIntExact(x - halo.window().minX());
        int localY = Math.toIntExact(y - halo.window().minY());
        return halo.sample(localX, localY) > 0d;
    }

    private int inferredInteriorityPpm(long membershipHeight) {
        long basePpm = Math.max(
                0L,
                Math.min((long) PPM, membershipHeight * PPM / Math.max(1L, baseTerrainAmplitude)));
        long numerator = (basePpm - recipe.coastBaseHeightPpm()) * PPM;
        return recipe.coastInteriorHeightPpm() <= 0
                ? PPM
                : LegacyV12Noise.clampPpm(numerator / recipe.coastInteriorHeightPpm());
    }

    private long reliefSignalPpm(long legacyX, long legacyY) {
        long upliftPpm = LegacyV12Noise.centeredPpm(LegacyV12Noise.organicValueNoise(
                random,
                LegacyV12Noise.UPLIFT,
                legacyX,
                legacyY,
                calibration.upliftScale(),
                recipe));
        long landformPpm = landformFieldPpm(legacyX, legacyY);
        int ridgePpm = ridgeCrestPpm(legacyX, legacyY);
        long rollingPpm = rollingFieldPpm(legacyX, legacyY);

        long macroSignalPpm = weightedCentered(upliftPpm, recipe.upliftWeightPpm())
                + weightedCentered(landformPpm, recipe.landformWeightPpm())
                + (long) ridgePpm * recipe.ridgeWeightPpm() * calibration.ruggednessPpm()
                        / PPM / PPM;
        macroSignalPpm = macroSignalPpm * calibration.reliefPpm() / PPM;

        long localSignalPpm = rollingPpm * recipe.rollingWeightPpm() / PPM;
        localSignalPpm = localSignalPpm * calibration.localReliefPpm() / PPM;

        long reliefSignalPpm = macroSignalPpm + localSignalPpm;
        if (reliefSignalPpm < 0L) {
            reliefSignalPpm = reliefSignalPpm * recipe.negativeReliefCompressionPpm() / PPM;
        }
        return reliefSignalPpm;
    }

    private long landformFieldPpm(long x, long y) {
        int spacing = calibration.landformSpacing();
        long latticeX = Math.floorDiv(x, spacing);
        long latticeY = Math.floorDiv(y, spacing);
        long xPpm = x * PPM;
        long yPpm = y * PPM;
        long sum = 0L;
        int neighborhood = recipe.featureNeighborhoodRadius();

        for (int offsetY = -neighborhood; offsetY <= neighborhood; offsetY++) {
            long featureY = latticeY + offsetY;
            for (int offsetX = -neighborhood; offsetX <= neighborhood; offsetX++) {
                long featureX = latticeX + offsetX;
                LandformFeature feature = createLandformFeature(featureX, featureY, spacing);
                long dx = xPpm - feature.centerXPpm();
                long dy = yPpm - feature.centerYPpm();
                long normalizedX = dx * PPM / feature.radiusPpm();
                long normalizedY = dy * PPM / feature.radiusPpm();
                long distanceSquaredPpm = (normalizedX * normalizedX + normalizedY * normalizedY) / PPM;
                if (distanceSquaredPpm >= PPM) continue;
                int falloffPpm = LegacyV12Noise.smoothStepPpm(PPM - distanceSquaredPpm);
                sum += (long) feature.signedMagnitudePpm() * falloffPpm / PPM;
            }
        }
        return LegacyV12Noise.clampCenteredPpm(sum);
    }

    private LandformFeature createLandformFeature(long featureX, long featureY, int spacing) {
        int jitterX = LegacyV12Noise.centeredRandomPpm(
                random, LegacyV12Noise.LANDFORM_FEATURE, featureX, featureY, 0L);
        int jitterY = LegacyV12Noise.centeredRandomPpm(
                random, LegacyV12Noise.LANDFORM_FEATURE, featureX, featureY, 1L);
        long centerX = featureX * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterX * spacing * recipe.featureJitterPpm() / PPM;
        long centerY = featureY * spacing * (long) PPM
                + (long) spacing * PPM / 2L
                + (long) jitterY * spacing * recipe.featureJitterPpm() / PPM;

        int radiusCoordinate = LegacyV12Noise.randomPpm(
                random, LegacyV12Noise.LANDFORM_FEATURE, featureX, featureY, 2L);
        int radiusFactorPpm = recipe.featureMinimumRadiusPpm()
                + (int) ((long) radiusCoordinate * recipe.featureRadiusRangePpm() / PPM);
        long radius = (long) spacing * radiusFactorPpm;

        int magnitudeCoordinate = LegacyV12Noise.randomPpm(
                random, LegacyV12Noise.LANDFORM_FEATURE, featureX, featureY, 3L);
        int magnitudePpm = recipe.featureMinimumMagnitudePpm()
                + (int) ((long) magnitudeCoordinate * recipe.featureMagnitudeRangePpm() / PPM);
        int sign = landformSign(featureX, featureY);
        return new LandformFeature(centerX, centerY, radius, sign * magnitudePpm);
    }

    private int landformSign(long featureX, long featureY) {
        long blockX = Math.floorDiv(featureX, recipe.featureBalanceBlockSize());
        long blockY = Math.floorDiv(featureY, recipe.featureBalanceBlockSize());
        int phase = LegacyV12Noise.randomPpm(
                random, LegacyV12Noise.LANDFORM_PATTERN, blockX, blockY, 0L) >= PPM / 2 ? 1 : 0;
        return ((featureX + featureY + phase) & 1L) == 0L ? 1 : -1;
    }

    private int ridgeCrestPpm(long x, long y) {
        int first = LegacyV12Noise.organicValueNoise(
                random, LegacyV12Noise.RIDGE_A, x, y, calibration.ridgeScale(), recipe);
        int second = LegacyV12Noise.organicValueNoise(
                random, LegacyV12Noise.RIDGE_B, x, y, calibration.ridgeScale(), recipe);
        long differencePpm = (long) Math.abs(first - second) * PPM / LegacyV12Noise.SAMPLE_MAX;
        int rawRidgePpm = LegacyV12Noise.clampPpm(PPM - differencePpm * 2L);
        int threshold = recipe.ridgeCrestThresholdPpm();
        if (rawRidgePpm <= threshold) return 0;
        long crestCoordinate = Math.min(
                (long) PPM,
                (rawRidgePpm - (long) threshold) * PPM / (PPM - (long) threshold));
        int smooth = LegacyV12Noise.smoothStepPpm(crestCoordinate);
        return (int) ((long) smooth * smooth / PPM);
    }

    private long rollingFieldPpm(long x, long y) {
        long primary = LegacyV12Noise.centeredPpm(LegacyV12Noise.smoothValueNoise(
                random, LegacyV12Noise.ROLLING, x, y, calibration.rollingScale()));
        long detail = LegacyV12Noise.centeredPpm(LegacyV12Noise.smoothValueNoise(
                random, LegacyV12Noise.ROLLING_DETAIL, x, y, calibration.rollingDetailScale()));
        return (primary * recipe.rollingPrimaryWeightPpm()
                + detail * recipe.rollingDetailWeightPpm()) / PPM;
    }

    private static long weightedCentered(long centeredPpm, int weightPpm) {
        return centeredPpm * weightPpm / PPM;
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / PPM;
    }

    private void validateWindow(ContinuumSampleWindow window) {
        if (window == null) throw new IllegalArgumentException("window must not be null");
        long maximumX = window.xAt(window.width() - 1);
        long maximumY = window.yAt(window.height() - 1);
        if (!domain.contains(window.minX(), window.minY()) || !domain.contains(maximumX, maximumY)) {
            throw new IllegalArgumentException("window lies outside native V15 relief domain");
        }
    }

    private record LandformFeature(
            long centerXPpm,
            long centerYPpm,
            long radiusPpm,
            int signedMagnitudePpm) {}
}
