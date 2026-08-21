package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRandom;

/**
 * Exact raster-sweep cache for the two smooth value-noise fields that define an organic warp.
 *
 * <p>The accepted V12 organic noise first samples warp X/Y at the unwarped world coordinate, turns
 * each sample into a bounded integer offset, then samples the purpose-specific field at the warped
 * coordinate. Only the first step is cached here. The purpose-specific field remains untouched,
 * which keeps this optimization independent of the non-monotone warped coordinate path.</p>
 */
final class OrganicWarpRowSampler {
    private static final int SAMPLE_MAX = 65_535;

    private final SmoothValueNoiseRowSampler warpX;
    private final SmoothValueNoiseRowSampler warpY;
    private final int amplitude;

    OrganicWarpRowSampler(
            GenerationRandom.BoundSampler warpXRandom,
            GenerationRandom.BoundSampler warpYRandom,
            int minX,
            int maxX,
            int scale,
            int amplitude) {
        if (warpXRandom == null || warpYRandom == null) {
            throw new IllegalArgumentException("organic warp random streams must not be null");
        }
        if (scale <= 0 || amplitude <= 0) {
            throw new IllegalArgumentException("organic warp scale/amplitude must be positive");
        }
        this.warpX = new SmoothValueNoiseRowSampler(warpXRandom, minX, maxX, scale);
        this.warpY = new SmoothValueNoiseRowSampler(warpYRandom, minX, maxX, scale);
        this.amplitude = amplitude;
    }

    /** Packs warped X into the high 32 bits and warped Y into the low 32 bits. */
    long warpedCoordinates(int x, int y) {
        int warpedX = x + centeredSampleOffset(warpX.sampleAt(x, y), amplitude);
        int warpedY = y + centeredSampleOffset(warpY.sampleAt(x, y), amplitude);
        return ((long) warpedX << 32) | (warpedY & 0xffff_ffffL);
    }

    private static int centeredSampleOffset(int sample, int amplitude) {
        long centered = (long) sample * 2L - SAMPLE_MAX;
        return (int) ((centered * amplitude) / SAMPLE_MAX);
    }
}
