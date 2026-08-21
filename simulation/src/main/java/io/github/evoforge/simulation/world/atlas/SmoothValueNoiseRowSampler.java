package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;

/**
 * Exact row-cached sampler for the deterministic smooth value-noise lattice used by V12.
 *
 * <p>The mathematical result is identical to sampling four lattice corners independently for every
 * cell. The sampler retains the two active lattice rows, precomputes the horizontal lattice address
 * and fade for every world X, and caches the vertical lattice address/fade once per raster row.
 * Memory remains O(width / scale + width), independent of world height.</p>
 */
final class SmoothValueNoiseRowSampler {
    private static final int SAMPLE_MAX = 65_535;
    private static final int PPM = NormalizedValue.SCALE;

    private final GenerationRandom.BoundSampler random;
    private final int minX;
    private final int maxX;
    private final int scale;
    private final long minLatticeX;
    private final int latticeWidth;
    private final int[] localLatticeXByWorldX;
    private final int[] horizontalFadeByWorldX;
    private int[] lowerSamples;
    private int[] upperSamples;
    private long cachedLowerLatticeY = Long.MIN_VALUE;
    private int cachedWorldY;
    private int cachedVerticalFade;
    private boolean hasCachedWorldY;

    SmoothValueNoiseRowSampler(
            GenerationRandom.BoundSampler random,
            int minX,
            int maxX,
            int scale) {
        if (random == null) throw new IllegalArgumentException("noise sampler random must not be null");
        if (minX > maxX || scale <= 0) {
            throw new IllegalArgumentException("noise sampler horizontal bounds/scale are invalid");
        }
        this.random = random;
        this.minX = minX;
        this.maxX = maxX;
        this.scale = scale;
        this.minLatticeX = Math.floorDiv((long) minX, scale);
        long maxLatticeX = Math.floorDiv((long) maxX, scale) + 1L;
        this.latticeWidth = Math.toIntExact(maxLatticeX - minLatticeX + 1L);
        this.lowerSamples = new int[latticeWidth];
        this.upperSamples = new int[latticeWidth];

        int worldWidth = Math.toIntExact((long) maxX - minX + 1L);
        this.localLatticeXByWorldX = new int[worldWidth];
        this.horizontalFadeByWorldX = new int[worldWidth];
        for (int localX = 0; localX < worldWidth; localX++) {
            long worldX = (long) minX + localX;
            long latticeX = Math.floorDiv(worldX, scale);
            int offsetX = (int) Math.floorMod(worldX, scale);
            localLatticeXByWorldX[localX] = Math.toIntExact(latticeX - minLatticeX);
            horizontalFadeByWorldX[localX] = fadeForOffset(offsetX, scale);
        }
    }

    int sampleAt(int x, int y) {
        if (x < minX || x > maxX) {
            throw new IllegalArgumentException("noise sample x lies outside cached world span");
        }
        if (!hasCachedWorldY || cachedWorldY != y) {
            long latticeY = Math.floorDiv((long) y, scale);
            ensureRows(latticeY);
            int offsetY = (int) Math.floorMod((long) y, scale);
            cachedWorldY = y;
            cachedVerticalFade = fadeForOffset(offsetY, scale);
            hasCachedWorldY = true;
        }

        int worldLocalX = Math.toIntExact((long) x - minX);
        int localLatticeX = localLatticeXByWorldX[worldLocalX];
        int horizontalFade = horizontalFadeByWorldX[worldLocalX];
        int lower = interpolateWithFade(
                lowerSamples[localLatticeX],
                lowerSamples[localLatticeX + 1],
                horizontalFade);
        int upper = interpolateWithFade(
                upperSamples[localLatticeX],
                upperSamples[localLatticeX + 1],
                horizontalFade);
        return interpolateWithFade(lower, upper, cachedVerticalFade);
    }

    private void ensureRows(long latticeY) {
        if (cachedLowerLatticeY == latticeY) return;
        if (cachedLowerLatticeY != Long.MIN_VALUE && latticeY == cachedLowerLatticeY + 1L) {
            int[] previousLower = lowerSamples;
            lowerSamples = upperSamples;
            upperSamples = previousLower;
            fillRow(upperSamples, latticeY + 1L);
            cachedLowerLatticeY = latticeY;
            return;
        }
        fillRow(lowerSamples, latticeY);
        fillRow(upperSamples, latticeY + 1L);
        cachedLowerLatticeY = latticeY;
    }

    private void fillRow(int[] target, long latticeY) {
        for (int localX = 0; localX < target.length; localX++) {
            target[localX] = sample(random, minLatticeX + localX, latticeY);
        }
    }

    private static int sample(
            GenerationRandom.BoundSampler random,
            long latticeX,
            long latticeY) {
        return (int) ((random.sampleLong(latticeX, latticeY, 0L, 0L) >>> 48) & SAMPLE_MAX);
    }

    private static int fadeForOffset(int offset, int scale) {
        long coordinate = ((long) offset * PPM) / scale;
        return smoothStepPpm(coordinate);
    }

    private static int interpolateWithFade(int from, int to, int fade) {
        return (int) (((long) from * (PPM - fade) + (long) to * fade) / PPM);
    }

    private static int smoothStepPpm(long coordinatePpm) {
        long coordinate = Math.max(0L, Math.min((long) PPM, coordinatePpm));
        long coordinateSquared = coordinate * coordinate;
        return (int) (coordinateSquared
                * (3L * PPM - 2L * coordinate)
                / ((long) PPM * PPM));
    }
}
