package io.github.evoforge.simulation.world.terrain;

import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileGenerator;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTileKey;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;

/**
 * Derived F2 map representation of the authoritative continuous Terrain surface.
 *
 * <p>The map requests a scale-aware observation when the concrete Terrain source supports it. This
 * filters only the disposable representation: exact Terrain truth remains unchanged. A one-sample
 * ghost border on every side permits centered slope estimates without tile-edge lighting seams.</p>
 *
 * <p>One byte carries two independent presentation facts. Bit 7 is land/ocean, bits 6..3 are
 * elevation/depth band, and bits 2..0 are hillshade. Elevation therefore owns hue while slope owns
 * brightness; a shadow can no longer turn a green lowland into a brown pseudo-mountain.</p>
 */
public final class TerrainSurfaceMapTileGenerator implements ContinuumMapTileGenerator {
    private static final double HILLSHADE_VERTICAL_EXAGGERATION = 34.0d;
    private static final double LIGHT_X = -0.4866643d;
    private static final double LIGHT_Y = 0.6083304d;
    private static final double LIGHT_Z = 0.6263284d;

    private static final int LAND_BIT = 0x80;
    private static final int ELEVATION_BANDS = 16;
    private static final int SHADE_BANDS = 8;

    private final ContinuumWorldDomain domain;
    private final ContinuousTerrainSurface surface;
    private final TerrainSurfaceMapObservation mapObservation;
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
        this.mapObservation = surface instanceof TerrainSurfaceMapObservation observation
                ? observation
                : null;
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

        int gridSide = sampleSide + 2;
        double[] z = new double[Math.multiplyExact(gridSide, gridSide)];
        for (int gridY = 0; gridY < gridSide; gridY++) {
            long worldY = coordinateWithGhost(originY, gridY - 1, step, maxY);
            int row = gridY * gridSide;
            for (int gridX = 0; gridX < gridSide; gridX++) {
                long worldX = coordinateWithGhost(originX, gridX - 1, step, maxX);
                z[row + gridX] = mapSampleAt(worldX, worldY, step);
            }
        }

        byte[] pixels = new byte[Math.multiplyExact(sampleSide, sampleSide)];
        for (int y = 0; y < sampleSide; y++) {
            int centerRow = (y + 1) * gridSide;
            int southRow = y * gridSide;
            int northRow = (y + 2) * gridSide;
            int pixelRow = y * sampleSide;
            for (int x = 0; x < sampleSide; x++) {
                int centerIndex = centerRow + x + 1;
                double center = z[centerIndex];
                double west = z[centerIndex - 1];
                double east = z[centerIndex + 1];
                double south = z[southRow + x + 1];
                double north = z[northRow + x + 1];
                pixels[pixelRow + x] = encode(center, west, east, south, north, step);
            }
        }
        return new ContinuumMapTile(key, sampleSide, pixels);
    }

    private double mapSampleAt(long x, long y, long step) {
        return mapObservation == null
                ? surface.surfaceZAt(x, y)
                : mapObservation.surfaceZForMapAt(x, y, step);
    }

    private static byte encode(
            double z,
            double west,
            double east,
            double south,
            double north,
            long step) {
        if (!Double.isFinite(z)
                || !Double.isFinite(west)
                || !Double.isFinite(east)
                || !Double.isFinite(south)
                || !Double.isFinite(north)) {
            return 0;
        }

        double slopeX = (east - west) / (2.0d * step);
        double slopeY = (north - south) / (2.0d * step);
        double nx = -slopeX * HILLSHADE_VERTICAL_EXAGGERATION;
        double ny = -slopeY * HILLSHADE_VERTICAL_EXAGGERATION;
        double nz = 1.0d;
        double inverseLength = 1.0d / Math.sqrt(nx * nx + ny * ny + nz * nz);
        nx *= inverseLength;
        ny *= inverseLength;
        nz *= inverseLength;
        double diffuse = Math.max(0.0d, nx * LIGHT_X + ny * LIGHT_Y + nz * LIGHT_Z);
        double shade = 0.47d + 0.53d * diffuse;
        int shadeBand = quantizeBand(shade, SHADE_BANDS);

        if (z < ContinuousTerrainSurface.SEA_DATUM) {
            double depth = Math.pow(clamp(-z / 3_300.0d, 0.0d, 1.0d), 0.60d);
            int depthBand = quantizeBand(depth, ELEVATION_BANDS);
            return (byte) ((depthBand << 3) | shadeBand);
        }

        double height = Math.pow(clamp(z / 3_000.0d, 0.0d, 1.0d), 0.58d);
        int heightBand = quantizeBand(height, ELEVATION_BANDS);
        return (byte) (LAND_BIT | (heightBand << 3) | shadeBand);
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

    private static long coordinateWithGhost(long origin, int sampleIndex, long step, long maximum) {
        long offset = Math.multiplyExact((long) sampleIndex, step);
        long coordinate = Math.addExact(origin, offset);
        return Math.max(0L, Math.min(maximum, coordinate));
    }

    private static int quantizeBand(double value, int bandCount) {
        double bounded = clamp(value, 0.0d, 1.0d);
        return Math.min(bandCount - 1, (int) Math.floor(bounded * bandCount));
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
