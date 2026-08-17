package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolume;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Deterministic first surface-hydrology model derived from drainage accumulation.
 *
 * <p>V1/V2 predate generated surface Water and therefore intentionally produce an empty field.
 * V3+ retain the same finite initial channel-Water algorithm plus a derived one-cell shoreline
 * relation. The runtime Liquid system remains the sole owner of subsequent redistribution.</p>
 */
public final class SurfaceHydrologyGenerationStage implements SurfaceHydrologyGenerator {
    private static final int[] DX = {-1, 0, 1, -1, 1, -1, 0, 1};
    private static final int[] DY = {-1, -1, -1, 0, 0, 1, 1, 1};
    private static final int MIN_CHANNEL_VOLUME = 300_000;
    private static final int MAX_CHANNEL_VOLUME = 700_000;

    @Override
    public SurfaceHydrologyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage) {
        if (genesis == null || elevation == null || drainage == null) {
            throw new IllegalArgumentException(
                    "surface hydrology generation dependencies must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!bounds.equals(elevation.bounds()) || !bounds.equals(drainage.bounds())) {
            throw new IllegalArgumentException(
                    "surface hydrology inputs must share genesis world bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        int[] initialWater = new int[area];
        boolean[] shoreline = new boolean[area];

        GenerationRevision revision = genesis.generationRevision();
        if (GenerationRevision.V1.equals(revision) || GenerationRevision.V2.equals(revision)) {
            return new DenseSurfaceHydrologyField(bounds, initialWater, shoreline);
        }
        if (!GenerationRevision.V3.equals(revision)
                && !GenerationRevision.V4.equals(revision)
                && !GenerationRevision.V5.equals(revision)) {
            throw new IllegalArgumentException(
                    "unsupported generation revision: " + revision.value());
        }

        long threshold = channelThreshold(area);
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int index = localY * width + localX;
                long contributing = drainage.contributingAreaAt(x, y);
                if (contributing < threshold || elevation.elevationAt(x, y) >= bounds.maxZ()) {
                    continue;
                }
                initialWater[index] = initialVolume(contributing, threshold, area);
            }
        }

        for (int localY = 0; localY < height; localY++) {
            for (int localX = 0; localX < width; localX++) {
                int index = localY * width + localX;
                if (initialWater[index] > CellVolume.EMPTY) continue;
                shoreline[index] = hasWetNeighbor(localX, localY, width, height, initialWater);
            }
        }

        return new DenseSurfaceHydrologyField(bounds, initialWater, shoreline);
    }

    private static long channelThreshold(int area) {
        long root = (long) StrictMath.sqrt(area);
        while (root * root < area) root++;
        while (root > 0L && (root - 1L) * (root - 1L) >= area) root--;
        return Math.max(4L, root);
    }

    private static int initialVolume(long contributing, long threshold, int area) {
        long excess = Math.max(0L, contributing - threshold);
        long denominator = Math.max(1L, (long) area - threshold);
        long span = (long) MAX_CHANNEL_VOLUME - MIN_CHANNEL_VOLUME;
        long scaled = MIN_CHANNEL_VOLUME + (span * excess) / denominator;
        return CellVolume.requireValid((int) Math.min(MAX_CHANNEL_VOLUME, scaled));
    }

    private static boolean hasWetNeighbor(
            int x,
            int y,
            int width,
            int height,
            int[] initialWater) {
        for (int neighbor = 0; neighbor < DX.length; neighbor++) {
            int nx = x + DX[neighbor];
            int ny = y + DY[neighbor];
            if (nx < 0 || nx >= width || ny < 0 || ny >= height) continue;
            if (initialWater[ny * width + nx] > CellVolume.EMPTY) return true;
        }
        return false;
    }
}
