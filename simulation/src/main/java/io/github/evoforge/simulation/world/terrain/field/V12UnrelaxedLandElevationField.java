package io.github.evoforge.simulation.world.terrain.field;

import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV12Noise;
import io.github.evoforge.simulation.world.terrain.genesis.LegacyV15Random;
import io.github.evoforge.simulation.world.terrain.genesis.V12LandRankPlan;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainCalibration;
import io.github.evoforge.simulation.world.terrain.genesis.V12TerrainRecipe;

/**
 * Accepted V12 terrestrial relief before the legacy in-place slope-relaxation pass.
 *
 * <p>Water returns a one-subunit negative membership sentinel because V14 later re-authors standing
 * water bathymetry. No whole-world elevation or land mask is retained.</p>
 *
 * <p>Contiguous bounded consumers can materialize a local window through {@link #fillWindow}. That
 * path builds only a request-local land-membership mask plus the historical coast-interiority halo,
 * so neighboring cells reuse the same exact rank decisions instead of asking the V12/V14 membership
 * field hundreds of times each. The authored height formula and coast-search order are unchanged.</p>
 */
public final class V12UnrelaxedLandElevationField implements TerrainElevationField {
    private static final int PPM = 1_000_000;

    private final ContinuumWorldDomain domain;
    private final LegacyV15Random random;
    private final V12LandRankPlan land;
    private final V12TerrainCalibration calibration;
    private final V12TerrainRecipe recipe;
    private final long landAmplitude;

    public V12UnrelaxedLandElevationField(
            ContinuumWorldDomain domain,
            long seed,
            V12LandRankPlan land,
            V12TerrainCalibration calibration,
            V12TerrainRecipe recipe,
            int maximumLandHeightCells) {
        if (domain == null || land == null || calibration == null || recipe == null) {
            throw new IllegalArgumentException("V12 elevation inputs must not be null");
        }
        if (maximumLandHeightCells <= 0) {
            throw new IllegalArgumentException("maximumLandHeightCells must be > 0");
        }
        this.domain = domain;
        this.random = new LegacyV15Random(seed);
        this.land = land;
        this.calibration = calibration;
        this.recipe = recipe;
        this.landAmplitude = Math.multiplyExact((long) maximumLandHeightCells, SUBUNITS_PER_CELL);
    }

    @Override
    public long elevationSubunitsAt(long x, long y) {
        requireCoordinate(x, y);
        if (!land.isLand(x, y)) return -1L;
        return authoredLandHeight(x, y, coastalInteriorityPpm(x, y));
    }

    /**
     * Fills one unit-resolution rectangular request exactly while reusing local membership decisions.
     * The retained working set is bounded by the request plus {@code coastTransitionCells} on each
     * side and is discarded by the caller with the output buffer.
     */
    void fillWindow(long minX, long minY, int width, int height, long[] target) {
        if (width <= 0 || height <= 0 || target == null
                || target.length < Math.multiplyExact(width, height)) {
            throw new IllegalArgumentException("V12 unrelaxed window dimensions/output are invalid");
        }
        long maxX = Math.addExact(minX, width - 1L);
        long maxY = Math.addExact(minY, height - 1L);
        if (!domain.contains(minX, minY) || !domain.contains(maxX, maxY)) {
            throw new IllegalArgumentException("V12 unrelaxed window lies outside the domain");
        }

        int transition = recipe.coastTransitionCells();
        long membershipMinX = Math.max(0L, minX - transition);
        long membershipMinY = Math.max(0L, minY - transition);
        long membershipMaxX = Math.min(domain.width() - 1L, maxX + transition);
        long membershipMaxY = Math.min(domain.height() - 1L, maxY + transition);
        int membershipWidth = Math.toIntExact(membershipMaxX - membershipMinX + 1L);
        int membershipHeight = Math.toIntExact(membershipMaxY - membershipMinY + 1L);
        boolean[] membership = new boolean[Math.multiplyExact(membershipWidth, membershipHeight)];

        int membershipCursor = 0;
        for (int localY = 0; localY < membershipHeight; localY++) {
            long worldY = membershipMinY + localY;
            for (int localX = 0; localX < membershipWidth; localX++, membershipCursor++) {
                membership[membershipCursor] = land.isLand(membershipMinX + localX, worldY);
            }
        }

        int cursor = 0;
        for (int localY = 0; localY < height; localY++) {
            long worldY = minY + localY;
            for (int localX = 0; localX < width; localX++, cursor++) {
                long worldX = minX + localX;
                if (!membershipAt(
                        membership,
                        membershipWidth,
                        membershipMinX,
                        membershipMinY,
                        worldX,
                        worldY)) {
                    target[cursor] = -1L;
                    continue;
                }
                int interiority = coastalInteriorityPpm(
                        worldX,
                        worldY,
                        membership,
                        membershipWidth,
                        membershipMinX,
                        membershipMinY);
                target[cursor] = authoredLandHeight(worldX, worldY, interiority);
            }
        }
    }

    private long authoredLandHeight(long x, long y, int interiorityPpm) {
        long legacyX = land.legacyX(x);
        long legacyY = land.legacyY(y);

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

        int coastGatePpm = recipe.coastMinimumReliefGatePpm()
                + (int) ((long) interiorityPpm * (PPM - recipe.coastMinimumReliefGatePpm()) / PPM);
        reliefSignalPpm = reliefSignalPpm * coastGatePpm / PPM;

        long baseHeightPpm = recipe.coastBaseHeightPpm()
                + (long) interiorityPpm * recipe.coastInteriorHeightPpm() / PPM;
        int heightPpm = LegacyV12Noise.clampPpm(baseHeightPpm + reliefSignalPpm);
        return positiveNormalizedHeight(heightPpm, landAmplitude);
    }

    private int coastalInteriorityPpm(long x, long y) {
        int transition = recipe.coastTransitionCells();
        for (int distance = 1; distance <= transition; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dy = distance - Math.abs(dx);
                long nx = x + dx;
                long firstY = y + dy;
                if (inside(nx, firstY) && !land.isLand(nx, firstY)) {
                    return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                }
                if (dy != 0) {
                    long secondY = y - dy;
                    if (inside(nx, secondY) && !land.isLand(nx, secondY)) {
                        return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                    }
                }
            }
        }
        return PPM;
    }

    private int coastalInteriorityPpm(
            long x,
            long y,
            boolean[] membership,
            int membershipWidth,
            long membershipMinX,
            long membershipMinY) {
        int transition = recipe.coastTransitionCells();
        for (int distance = 1; distance <= transition; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                int dy = distance - Math.abs(dx);
                long nx = x + dx;
                long firstY = y + dy;
                if (inside(nx, firstY)
                        && !membershipAt(
                                membership,
                                membershipWidth,
                                membershipMinX,
                                membershipMinY,
                                nx,
                                firstY)) {
                    return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                }
                if (dy != 0) {
                    long secondY = y - dy;
                    if (inside(nx, secondY)
                            && !membershipAt(
                                    membership,
                                    membershipWidth,
                                    membershipMinX,
                                    membershipMinY,
                                    nx,
                                    secondY)) {
                        return LegacyV12Noise.smoothStepPpm((long) distance * PPM / transition);
                    }
                }
            }
        }
        return PPM;
    }

    private static boolean membershipAt(
            boolean[] membership,
            int membershipWidth,
            long membershipMinX,
            long membershipMinY,
            long x,
            long y) {
        int localX = Math.toIntExact(x - membershipMinX);
        int localY = Math.toIntExact(y - membershipMinY);
        return membership[Math.addExact(Math.multiplyExact(localY, membershipWidth), localX)];
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

    private boolean inside(long x, long y) {
        return domain.contains(x, y);
    }

    private void requireCoordinate(long x, long y) {
        if (!domain.contains(x, y)) {
            throw new IllegalArgumentException("coordinate lies outside the V12 elevation domain");
        }
    }

    private static long weightedCentered(long centeredPpm, int weightPpm) {
        return centeredPpm * weightPpm / PPM;
    }

    private static long positiveNormalizedHeight(int heightPpm, long amplitude) {
        if (amplitude <= 1L) return Math.max(1L, amplitude);
        return 1L + ((amplitude - 1L) * heightPpm) / PPM;
    }

    private record LandformFeature(
            long centerXPpm,
            long centerYPpm,
            long radiusPpm,
            int signedMagnitudePpm) {}
}
