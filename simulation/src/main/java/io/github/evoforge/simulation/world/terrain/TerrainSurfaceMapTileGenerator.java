package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileKey;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Derived F2 map representation of the authoritative continuous Terrain surface.
 *
 * <p>Each tile samples one extra row/column of raw Z so hillshade can be computed from actual
 * neighbouring Terrain heights without tile-edge discontinuities. The requested Continuum level
 * controls sample spacing only; Terrain truth remains {@link ContinuousTerrainSurface}.</p>
 */
public final class TerrainSurfaceMapTileGenerator implements ContinuumMapTileGenerator {
    private static final double HILLSHADE_VERTICAL_EXAGGERATION = 46.0d;
    private static final double LIGHT_X = -0.4454354d;
    private static final double LIGHT_Y = 0.8181489d;
    private static final double LIGHT_Z = 0.3636910d;

    private final ContinuumWorldDomain domain;
    private final ContinuousTerrainSurface surface;
    private final int sampleSide;
    private final int maxLevel;

    public TerrainSurfaceMapTileGenerator(
            ContinuumWorldDomain domain,
            ContinuousTerrainSurface surface,
            int sampleSide) {
        if (domain == null || surface == null) {
            throw new IllegalArgumentException("domain and surface must not be null");
        }
        if (sampleSide <= 0) throw new IllegalArgumentException("sampleSide must be > 0");
        this.domain = domain;
        this.surface = surface;
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

    @Override
    public ContinuumMapTile generate(ContinuumMapTileKey key) {
        requireValidKey(key);
        long step = 1L << key.level();
        long span = tileWorldSpan(key.level());
        long originX = Math.multiplyExact(key.tileX(), span);
        long originY = Math.multiplyExact(key.tileY(), span);
        long maxX = domain.width() - 1L;
        long maxY = domain.height() - 1L;

        int gridSide = sampleSide + 1;
        double[] z = new double[Math.multiplyExact(gridSide, gridSide)];
        for (int y = 0; y < gridSide; y++) {
            long worldY = coordinate(originY, y, step, maxY);
            int row = y * gridSide;
            for (int x = 0; x < gridSide; x++) {
                long worldX = coordinate(originX, x, step, maxX);
                z[row + x] = surface.surfaceZAt(worldX, worldY);
            }
        }

        byte[] pixels = new byte[Math.multiplyExact(sampleSide, sampleSide)];
        for (int y = 0; y < sampleSide; y++) {
            int gridRow = y * gridSide;
            int pixelRow = y * sampleSide;
            for (int x = 0; x < sampleSide; x++) {
                double center = z[gridRow + x];
                double east = z[gridRow + x + 1];
                double north = z[gridRow + gridSide + x];
                pixels[pixelRow + x] = encode(center, east, north, step);
            }
        }
        return new ContinuumMapTile(key, sampleSide, pixels);
    }

    private byte encode(double z, double east, double north, long step) {
        if (!Double.isFinite(z) || !Double.isFinite(east) || !Double.isFinite(north)) return 0;
        double slopeX = (east - z) / step;
        double slopeY = (north - z) / step;
        double nx = -slopeX * HILLSHADE_VERTICAL_EXAGGERATION;
        double ny = 1.0d;
        double nz = -slopeY * HILLSHADE_VERTICAL_EXAGGERATION;
        double inverseLength = 1.0d / Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx *= inverseLength;
        ny *= inverseLength;
        nz *= inverseLength;
        double diffuse = Math.max(0.0d, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        double shade = 0.34d + 0.66d * diffuse;
        double slopeStrength = clamp(Math.hypot(slopeX, slopeY) * 18.0d, 0.0d, 1.0d);

        double normalized;
        if (z < ContinuousTerrainSurface.SEA_DATUM) {
            double depth = Math.pow(clamp(-z / 3_300.0d, 0.0d, 1.0d), 0.58d);
            normalized = 0.445d - 0.300d * depth + 0.055d * (shade - 0.62d);
            normalized = clamp(normalized, 0.015d, 0.495d);
        } else {
            double height = Math.pow(clamp(z / 2_800.0d, 0.0d, 1.0d), 0.54d);
            normalized = 0.565d
                    + 0.275d * height
                    + 0.170d * (shade - 0.62d)
                    + 0.045d * slopeStrength;
            normalized = clamp(normalized, 0.505d, 1.0d);
        }
        return quantize(normalized);
    }

    private void requireValidKey(ContinuumMapTileKey key) {
        if (key == null) throw new IllegalArgumentException("key must not be null");
        if (key.level() > maxLevel) throw new IllegalArgumentException("tile level exceeds maxLevel");
        long span = tileWorldSpan(key.level());
        long countX = ceilDiv(domain.width(), span);
        long countY = ceilDiv(domain.height(), span);
        if (key.tileX() >= countX || key.tileY() >= countY) {
            throw new IllegalArgumentException("tile outside logical domain");
        }
    }

    private static long coordinate(long origin, int index, long step, long maximum) {
        long offset = Math.multiplyExact((long) index, step);
        if (origin >= maximum || offset >= maximum - origin) return maximum;
        return origin + offset;
    }

    private static byte quantize(double value) {
        if (Double.isNaN(value) || value <= 0.0d) return 0;
        if (value >= 1.0d) return (byte) 0xFF;
        return (byte) (int) (value * 255.0d + 0.5d);
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

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
