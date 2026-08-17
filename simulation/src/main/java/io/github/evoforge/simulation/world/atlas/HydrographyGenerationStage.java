package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Deterministic generated channel network derived from drainage accumulation and available surface
 * headroom.
 *
 * <p>V1/V2 predate generated surface Water and expose no channels. V3+ preserve the same durable
 * channel footprint. Climate may affect initial water but never whether this generated channel fact
 * exists, so dry climates can retain dry channels.</p>
 */
public final class HydrographyGenerationStage implements HydrographyGenerator {

    @Override
    public HydrographyField generate(
            WorldGenesis genesis,
            ElevationField elevation,
            DrainageField drainage) {
        if (genesis == null || elevation == null || drainage == null) {
            throw new IllegalArgumentException("hydrography generation inputs must not be null");
        }
        WorldBounds bounds = genesis.spec().bounds();
        if (!bounds.equals(elevation.bounds()) || !bounds.equals(drainage.bounds())) {
            throw new IllegalArgumentException("hydrography inputs must share genesis world bounds");
        }

        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        int area = Math.multiplyExact(width, height);
        boolean[] channels = new boolean[area];

        GenerationRevision revision = genesis.generationRevision();
        if (GenerationRevision.V1.equals(revision) || GenerationRevision.V2.equals(revision)) {
            return new DenseHydrographyField(bounds, channels);
        }
        if (!GenerationRevision.V3.equals(revision)
                && !GenerationRevision.V4.equals(revision)
                && !GenerationRevision.V5.equals(revision)
                && !GenerationRevision.V6.equals(revision)
                && !GenerationRevision.V7.equals(revision)
                && !GenerationRevision.V8.equals(revision)) {
            throw new IllegalArgumentException(
                    "unsupported generation revision: " + revision.value());
        }

        long threshold = channelThreshold(area);
        int index = 0;
        for (long y = bounds.minY(); y <= (long) bounds.maxY(); y++) {
            int worldY = (int) y;
            for (long x = bounds.minX(); x <= (long) bounds.maxX(); x++) {
                int worldX = (int) x;
                long contributing = drainage.contributingAreaAt(worldX, worldY);
                channels[index++] = contributing >= threshold
                        && elevation.elevationAt(worldX, worldY) < bounds.maxZ();
            }
        }
        return new DenseHydrographyField(bounds, channels);
    }

    static long channelThreshold(int area) {
        if (area <= 0) throw new IllegalArgumentException("world area must be positive");
        long root = (long) StrictMath.sqrt(area);
        while (root * root < area) root++;
        while (root > 0L && (root - 1L) * (root - 1L) >= area) root--;
        return Math.max(4L, root);
    }
}
