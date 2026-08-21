package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.genesis.GenerationRandom;

/**
 * Exact raster-row cache for the double-valued smooth lattice noise used by landmass synthesis.
 *
 * <p>The sampler preserves the existing arithmetic order: lattice coordinates are still obtained
 * from {@link StrictMath#floor(double)}, lattice values still come from the same bound RNG stream,
 * and interpolation is still the same cubic smooth-step lerp. Only repeated lattice sampling and
 * repeated X-coordinate setup are removed. Memory is O(width + width / scale), independent of
 * world height.</p>
 */
final class SmoothDoubleNoiseRowSampler {
    private static final int PPM = NormalizedValue.SCALE;
    private static final int SAMPLE_MAX = 65_535;

    private final GenerationRandom.BoundSampler random;
    private final int minX;
    private final int maxX;
    private final double scale;
    private final long minLatticeX;
    private final int[] localLatticeXByWorldX;
    private final double[] smoothXByWorldX;
    private double[] lowerSamples;
    private double[] upperSamples;
    private long cachedLowerLatticeY = Long.MIN_VALUE;
    private int cachedWorldY;
    private double cachedSmoothY;
    private boolean hasCachedWorldY;

    SmoothDoubleNoiseRowSampler(
            GenerationRandom.BoundSampler random,
            int minX,
            int maxX,
            double scale) {
        if (random == null) throw new IllegalArgumentException("noise sampler random must not be null");
        if (minX > maxX || !(scale > 0d) || !Double.isFinite(scale)) {
            throw new IllegalArgumentException("noise sampler horizontal bounds/scale are invalid");
        }
        this.random = random;
        this.minX = minX;
        this.maxX = maxX;
        this.scale = scale;

        this.minLatticeX = floorLattice(minX, scale);
        long maxLatticeX = floorLattice(maxX, scale) + 1L;
        int latticeWidth = Math.toIntExact(maxLatticeX - minLatticeX + 1L);
        this.lowerSamples = new double[latticeWidth];
        this.upperSamples = new double[latticeWidth];

        int width = Math.toIntExact((long) maxX - minX + 1L);
        this.localLatticeXByWorldX = new int[width];
        this.smoothXByWorldX = new double[width];
        for (int localX = 0; localX < width; localX++) {
            int worldX = Math.toIntExact((long) minX + localX);
            double gridX = worldX / scale;
            long latticeX = (long) StrictMath.floor(gridX);
            localLatticeXByWorldX[localX] = Math.toIntExact(latticeX - minLatticeX);
            smoothXByWorldX[localX] = smooth(gridX - latticeX);
        }
    }

    double sampleAt(int x, int y) {
        if (x < minX || x > maxX) {
            throw new IllegalArgumentException("noise sample x lies outside cached world span");
        }
        if (!hasCachedWorldY || cachedWorldY != y) {
            double gridY = y / scale;
            long latticeY = (long) StrictMath.floor(gridY);
            ensureRows(latticeY);
            cachedWorldY = y;
            cachedSmoothY = smooth(gridY - latticeY);
            hasCachedWorldY = true;
        }

        int worldLocalX = Math.toIntExact((long) x - minX);
        int localLatticeX = localLatticeXByWorldX[worldLocalX];
        double smoothX = smoothXByWorldX[worldLocalX];
        double a = lowerSamples[localLatticeX];
        double b = lowerSamples[localLatticeX + 1];
        double c = upperSamples[localLatticeX];
        double d = upperSamples[localLatticeX + 1];
        double top = a + (b - a) * smoothX;
        double bottom = c + (d - c) * smoothX;
        return top + (bottom - top) * cachedSmoothY;
    }

    private void ensureRows(long latticeY) {
        if (cachedLowerLatticeY == latticeY) return;
        if (cachedLowerLatticeY != Long.MIN_VALUE && latticeY == cachedLowerLatticeY + 1L) {
            double[] previousLower = lowerSamples;
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

    private void fillRow(double[] target, long latticeY) {
        for (int localX = 0; localX < target.length; localX++) {
            target[localX] = centeredUnit(minLatticeX + localX, latticeY);
        }
    }

    private double centeredUnit(long latticeX, long latticeY) {
        int sample = (int) ((random.sampleLong(latticeX, latticeY, 0L, 0L) >>> 48) & SAMPLE_MAX);
        int ppm = (int) ((long) sample * PPM / SAMPLE_MAX);
        return ppm / (double) PPM * 2d - 1d;
    }

    private static long floorLattice(int coordinate, double scale) {
        return (long) StrictMath.floor(coordinate / scale);
    }

    private static double smooth(double value) {
        return value * value * (3d - 2d * value);
    }
}
