package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;

/**
 * Exact row-cached sampler for the deterministic smooth value-noise lattice used by V12.
 *
 * <p>The mathematical result is identical to sampling four lattice corners independently for every
 * cell. The sampler instead retains the two lattice rows that bound the current world Y and hashes
 * each lattice point at most once while that row is active. Memory is O(width / scale), independent
 * of world height.</p>
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
    private int[] lowerSamples;
    private int[] upperSamples;
    private long cachedLowerLatticeY = Long.MIN_VALUE;

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
    }

    int sampleAt(int x, int y) {
        if (x < minX || x > maxX) {
            throw new IllegalArgumentException("noise sample x lies outside cached world span");
        }
        long latticeY = Math.floorDiv((long) y, scale);
        ensureRows(latticeY);

        long latticeX = Math.floorDiv((long) x, scale);
        int localX = Math.toIntExact(latticeX - minLatticeX);
        int offsetX = (int) Math.floorMod((long) x, scale);
        int offsetY = (int) Math.floorMod((long) y, scale);
        int lower = smoothInterpolate(
                lowerSamples[localX],
                lowerSamples[localX + 1],
                offsetX,
                scale);
        int upper = smoothInterpolate(
                upperSamples[localX],
                upperSamples[localX + 1],
                offsetX,
                scale);
        return smoothInterpolate(lower, upper, offsetY, scale);
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

    private static int smoothInterpolate(int from, int to, int offset, int scale) {
        long coordinate = ((long) offset * PPM) / scale;
        int fade = smoothStepPpm(coordinate);
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
