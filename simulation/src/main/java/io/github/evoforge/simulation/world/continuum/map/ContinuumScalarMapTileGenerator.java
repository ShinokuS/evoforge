package io.github.evoforge.simulation.world.continuum.map;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarField;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Directly samples the same coordinate-addressed Continuum field at the tile's resolution.
 * It never generates exact detail and then downsamples it.
 */
public final class ContinuumScalarMapTileGenerator implements ContinuumMapTileGenerator {
    private final ContinuumWorldDomain domain;
    private final ContinuumScalarField field;
    private final int sampleSide;
    private final int maxLevel;

    public ContinuumScalarMapTileGenerator(
            ContinuumWorldDomain domain,
            ContinuumScalarField field,
            int sampleSide) {
        if (domain == null) throw new IllegalArgumentException("domain must not be null");
        if (field == null) throw new IllegalArgumentException("field must not be null");
        if (sampleSide <= 0) throw new IllegalArgumentException("sampleSide must be > 0");
        this.domain = domain;
        this.field = field;
        this.sampleSide = sampleSide;
        this.maxLevel = computeMaxLevel(domain, sampleSide);
    }

    public int sampleSide() {
        return sampleSide;
    }

    public int maxLevel() {
        return maxLevel;
    }

    public long tileWorldSpan(int level) {
        if (level < 0 || level > maxLevel) throw new IllegalArgumentException("level outside map hierarchy");
        return Math.multiplyExact((long) sampleSide, 1L << level);
    }

    public long tileCountX(int level) {
        return ceilDiv(domain.width(), tileWorldSpan(level));
    }

    public long tileCountY(int level) {
        return ceilDiv(domain.height(), tileWorldSpan(level));
    }

    @Override
    public ContinuumMapTile generate(ContinuumMapTileKey key) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (key.level() > maxLevel) throw new IllegalArgumentException("tile level exceeds maxLevel");
        if (key.tileX() >= tileCountX(key.level()) || key.tileY() >= tileCountY(key.level())) {
            throw new IllegalArgumentException("tile outside logical domain");
        }

        long step = 1L << key.level();
        long span = tileWorldSpan(key.level());
        long originX = Math.multiplyExact(key.tileX(), span);
        long originY = Math.multiplyExact(key.tileY(), span);
        byte[] pixels = new byte[Math.multiplyExact(sampleSide, sampleSide)];

        for (int y = 0; y < sampleSide; y++) {
            long worldY = Math.min(domain.height() - 1L, Math.addExact(originY, Math.multiplyExact((long) y, step)));
            for (int x = 0; x < sampleSide; x++) {
                long worldX = Math.min(domain.width() - 1L, Math.addExact(originX, Math.multiplyExact((long) x, step)));
                double value = clamp01(field.sample(worldX, worldY));
                pixels[y * sampleSide + x] = (byte) Math.round(value * 255d);
            }
        }
        return new ContinuumMapTile(key, sampleSide, pixels);
    }

    private static int computeMaxLevel(ContinuumWorldDomain domain, int sampleSide) {
        long largest = Math.max(domain.width(), domain.height());
        long span = sampleSide;
        int level = 0;
        while (span < largest) {
            if (span > Long.MAX_VALUE / 2L) return level;
            span *= 2L;
            level++;
        }
        return level;
    }

    private static long ceilDiv(long value, long divisor) {
        return 1L + (value - 1L) / divisor;
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value)) return 0d;
        return Math.max(0d, Math.min(1d, value));
    }
}
