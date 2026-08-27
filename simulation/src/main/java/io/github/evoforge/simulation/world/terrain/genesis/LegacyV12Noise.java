package io.github.evoforge.simulation.world.terrain.genesis;

/** Exact fixed-point noise and feature primitives from the accepted V12 terrain implementation. */
public final class LegacyV12Noise {
    public static final int PPM = 1_000_000;
    public static final int SAMPLE_MAX = 65_535;

    public static final String LANDMASS = "world:landmass";
    public static final String FRAGMENT = "world:fragment";
    public static final String UPLIFT = "world:v12-uplift";
    public static final String RIDGE_A = "world:v12-ridge-a";
    public static final String RIDGE_B = "world:v12-ridge-b";
    public static final String ROLLING = "world:v12-rolling";
    public static final String ROLLING_DETAIL = "world:v12-rolling-detail";
    public static final String LANDFORM_FEATURE = "world:v12-landform-feature";
    public static final String LANDFORM_PATTERN = "world:v12-landform-pattern";
    public static final String WARP_X = "world:v12-warp-x";
    public static final String WARP_Y = "world:v12-warp-y";

    private LegacyV12Noise() {}

    public static int organicValueNoise(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            int scale,
            V12TerrainRecipe recipe) {
        int warpScale = Math.max(recipe.minimumWarpScale(), scale * recipe.warpScaleMultiplier());
        int warpAmplitude = Math.max(1, scale / recipe.warpAmplitudeDivisor());
        int warpXSample = smoothValueNoise(random, WARP_X, x, y, warpScale);
        int warpYSample = smoothValueNoise(random, WARP_Y, x, y, warpScale);
        long warpedX = x + centeredSampleOffset(warpXSample, warpAmplitude);
        long warpedY = y + centeredSampleOffset(warpYSample, warpAmplitude);
        return smoothValueNoise(random, purpose, warpedX, warpedY, scale);
    }

    public static int smoothValueNoise(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            int scale) {
        long latticeX = Math.floorDiv(x, scale);
        long latticeY = Math.floorDiv(y, scale);
        int offsetX = (int) Math.floorMod(x, scale);
        int offsetY = (int) Math.floorMod(y, scale);
        int lowerLeft = sample(random, purpose, latticeX, latticeY);
        int lowerRight = sample(random, purpose, latticeX + 1L, latticeY);
        int upperLeft = sample(random, purpose, latticeX, latticeY + 1L);
        int upperRight = sample(random, purpose, latticeX + 1L, latticeY + 1L);
        int lower = smoothInterpolate(lowerLeft, lowerRight, offsetX, scale);
        int upper = smoothInterpolate(upperLeft, upperRight, offsetX, scale);
        return smoothInterpolate(lower, upper, offsetY, scale);
    }

    public static int randomPpm(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            long ordinal) {
        int sample = (int) ((random.sampleElevation(purpose, x, y, ordinal) >>> 48) & SAMPLE_MAX);
        return sampleToPpm(sample);
    }

    public static int centeredRandomPpm(
            LegacyV15Random random,
            String purpose,
            long x,
            long y,
            long ordinal) {
        return randomPpm(random, purpose, x, y, ordinal) * 2 - PPM;
    }

    public static long centeredPpm(int sample) {
        return (long) sampleToPpm(sample) * 2L - PPM;
    }

    public static int sampleToPpm(int sample) {
        return (int) ((long) sample * PPM / SAMPLE_MAX);
    }

    public static int ppmToSample(int ppm) {
        return (int) ((long) clampPpm(ppm) * SAMPLE_MAX / PPM);
    }

    public static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) PPM, coordinatePpm));
        long coordinateSquared = coordinate * coordinate;
        return (int) (coordinateSquared
                * (3L * PPM - 2L * coordinate)
                / ((long) PPM * PPM));
    }

    public static int clampPpm(long value) {
        return (int) Math.max(0L, Math.min((long) PPM, value));
    }

    public static long clampCenteredPpm(long value) {
        return Math.max(-(long) PPM, Math.min((long) PPM, value));
    }

    private static int sample(
            LegacyV15Random random,
            String purpose,
            long latticeX,
            long latticeY) {
        return (int) ((random.sampleElevation(purpose, latticeX, latticeY, 0L) >>> 48) & SAMPLE_MAX);
    }

    private static int smoothInterpolate(int from, int to, int offset, int scale) {
        long coordinate = ((long) offset * PPM) / scale;
        int fade = smoothStepPpm(coordinate);
        return (int) (((long) from * (PPM - fade) + (long) to * fade) / PPM);
    }

    private static int centeredSampleOffset(int sample, int amplitude) {
        long centered = (long) sample * 2L - SAMPLE_MAX;
        return (int) ((centered * amplitude) / SAMPLE_MAX);
    }
}
