package io.github.evoforge.simulation.world.geology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Dense immutable geology representation using compact unit ordinals plus per-column provinces. */
final class DenseGeologyField implements GeologyField {
    private final WorldBounds bounds;
    private final CompiledGeologyProfile profile;
    private final int width;
    private final int height;
    private final int depth;
    private final char[] unitOrdinals;
    private final long[] provinceIds;

    DenseGeologyField(
            WorldBounds bounds,
            CompiledGeologyProfile profile,
            char[] unitOrdinals,
            long[] provinceIds) {
        if (bounds == null || profile == null || unitOrdinals == null || provinceIds == null) {
            throw new IllegalArgumentException("geology field dependencies must not be null");
        }
        width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        depth = Math.toIntExact((long) bounds.maxZ() - bounds.minZ() + 1L);
        int area = Math.multiplyExact(width, height);
        int volume = Math.multiplyExact(area, depth);
        if (provinceIds.length != area || unitOrdinals.length != volume) {
            throw new IllegalArgumentException("geology arrays must match world bounds");
        }
        for (char ordinal : unitOrdinals) {
            if (ordinal >= profile.units().size()) {
                throw new IllegalArgumentException("geology unit ordinal outside compiled profile");
            }
        }
        this.bounds = bounds;
        this.profile = profile;
        this.unitOrdinals = unitOrdinals.clone();
        this.provinceIds = provinceIds.clone();
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public CompiledGeologyProfile profile() {
        return profile;
    }

    @Override
    public GeologyUnitKey unitAt(int x, int y, int z) {
        return profile.units().get(unitOrdinals[volumeIndex(x, y, z)]);
    }

    @Override
    public long provinceIdAt(int x, int y) {
        return provinceIds[columnIndex(x, y)];
    }

    private int volumeIndex(int x, int y, int z) {
        requireContains(x, y, z);
        int localZ = z - bounds.minZ();
        return localZ * width * height + columnIndex(x, y);
    }

    private int columnIndex(int x, int y) {
        if (x < bounds.minX() || x > bounds.maxX()
                || y < bounds.minY() || y > bounds.maxY()) {
            throw new IllegalArgumentException(
                    "geology position outside world bounds: (" + x + ", " + y + ")");
        }
        return (y - bounds.minY()) * width + (x - bounds.minX());
    }

    private void requireContains(int x, int y, int z) {
        if (!contains(x, y, z)) {
            throw new IllegalArgumentException(
                    "geology position outside world bounds: ("
                            + x + ", " + y + ", " + z + ")");
        }
    }
}
