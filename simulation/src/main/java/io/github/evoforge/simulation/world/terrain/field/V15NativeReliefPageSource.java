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
 * Restores the accepted V12/V15 terrestrial relief frequencies after large-world macro planning.
 *
 * <p>Only global morphology is allowed to come from the bounded planning field. Historical V12
 * uplift, landforms, ridges and rolling detail have authored scales measured in real terrain cells
 * (mostly tens of cells), so scaling a finished 300-cell raster across a 10k-cell domain is wrong.
 * This source evaluates those accepted local laws directly in the declared world's coordinates and
 * adds their zero-centered relief to the macro V15 land elevation. Water is left untouched for the
 * V15 lake/bathymetry branch.
 */
public final class V15NativeReliefPageSource implements ContinuumScalarPageSource {
    private static final int PPM = 1_000_000;

    private final ContinuumWorldDomain domain;
    private final ContinuumScalarPageSource macroBase;
    private final LegacyV15Random random;
    private final V15TerrainCoordinateFrame frame;
    private final V12TerrainCalibration calibration;
    private final V12TerrainRecipe recipe;
    private final long baseTerrainAmplitude;
    private final long maximumLandElevation;

    public V15NativeReliefPageSource(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition definition,
            ContinuumScalarPageSource macroBase,
            int baseTerrainCeilingCells,
            int maximumZCells) {
        if (domain == null || definition == null || macroBase == null) {
            throw new IllegalArgumentException("native V15 relief inputs must not be null");
        }
        if (!domain.equals(macroBase.domain())) {
            throw new IllegalArgumentException("native V15 relief base must match its domain");
        }
        if (baseTerrainCeilingCells <= 0 || maximumZCells <= 0) {
            throw new IllegalArgumentException("V15 relief height bounds must be positive");
        }
        this.domain = domain;
        this.macroBase = macroBase;
        this.random = new LegacyV15Random(seed);
        this.frame = V15TerrainCoordinateFrame.centered(domain);
        this.recipe = V12TerrainRecipe.balanced();
        this.calibration = V12TerrainCalibration.compile(domain, definition, recipe);
        this.baseTerrainAmplitude = Math.multiplyExact(
                (long) baseTerrainCeilingCells,
                TerrainElevationField.SUBUNITS_PER_CELL);
        this.maximumLandElevation = Math.multiplyExact(
                (long) maximumZCells,
                TerrainElevationField.SUBUNITS_PER_CELL);
    }

    @Override
    public ContinuumWorldDomain domain() {
        return domain;
    }

    @Override
    public ContinuumScalarPage materialize(ContinuumSampleWindow window) {
        validateWindow(window);
        ContinuumScalarPage macro = macroBase.materialize(window);
        double[] output = new double[Math.multiplyExact(window.width(), window.height())];
        int cursor = 0;
        for (int sampleY = 0; sampleY < window.height(); sampleY++) {
            long y = window.yAt(sampleY);
            for (int sampleX = 0; sampleX < window.width(); sampleX++) {
                long x = window.xAt(sampleX);
                long macroHeight = Math.round(macro.sample(sampleX, sampleY));
                if (macroHeight <= 0L) {
                    output[cursor++] = macroHeight;
                    continue;
                }

                long reliefPpm = reliefSignalPpm(frame.legacyX(x), frame.legacyY(y));
                int coastGatePpm = inferredCoastGatePpm(macroHeight);
                reliefPpm = reliefPpm * coastGatePpm / PPM;
                long delta = reliefPpm * baseTerrainAmplitude / PPM;
                long detailed = Math.max(
                        1L,
                        Math.min(maximumLandElevation, Math.addExact(macroHeight, delta)));
                output[cursor++] = detailed;
            }
        }
        return new ContinuumScalarPage(window, output);
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

    /**
     * The historical coast gate depends on distance to the exact land mask. Large-world macro
     * planning already encoded that coast profile in its positive base height, so infer the same
     * transition coordinate from the V12 coast base/interior band without scanning neighboring
     * simulation cells.
     */
    private int inferredCoastGatePpm(long macroHeight) {
        long basePpm = Math.max(
                0L,
                Math.min((long) PPM, macroHeight * PPM / Math.max(1L, baseTerrainAmplitude)));
        long numerator = (basePpm - recipe.coastBaseHeightPpm()) * PPM;
        int interiorityPpm = recipe.coastInteriorHeightPpm() <= 0
                ? PPM
                : LegacyV12Noise.clampPpm(numerator / recipe.coastInteriorHeightPpm());
        return recipe.coastMinimumReliefGatePpm()
                + (int) ((long) interiorityPpm
                        * (PPM - recipe.coastMinimumReliefGatePpm()) / PPM);
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
