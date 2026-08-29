package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/** Immutable generated surface-geometry representation for one horizontal world footprint. */
public interface TerrainShapeField {
    WorldBounds bounds();

    /** Geometry selected to represent the generated surface at this column. */
    TerrainSurfacePatch surfaceAt(int x, int y);

    /** Runtime Shape override, or {@code null} when ordinary full-cell geometry is sufficient. */
    Shape shapeOverrideAt(int x, int y);

    /**
     * Number of shape overrides represented by this field. Dense finite fields report the exact
     * whole-world count. Lazy Continuum fields report only the currently materialized cache and pair
     * this value with {@link #overrideCountIsExact()} returning {@code false}; asking for telemetry
     * must never force traversal of an otherwise-unmaterialized world.
     */
    long overrideCount();

    /** Whether {@link #overrideCount()} is the exact whole-world count. */
    default boolean overrideCountIsExact() {
        return true;
    }

    /** Compatibility field preserving ordinary full-cell surface geometry. */
    static TerrainShapeField baseline(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("terrain shape bounds must not be null");
        TerrainSurfacePatch surface = TerrainSurfacePatch.flatTop();
        return new TerrainShapeField() {
            @Override public WorldBounds bounds() { return bounds; }
            @Override public TerrainSurfacePatch surfaceAt(int x, int y) {
                requireColumn(bounds, x, y);
                return surface;
            }
            @Override public Shape shapeOverrideAt(int x, int y) {
                requireColumn(bounds, x, y);
                return null;
            }
            @Override public long overrideCount() { return 0L; }
        };
    }

    private static void requireColumn(WorldBounds bounds, int x, int y) {
        if (x < bounds.minX() || x > bounds.maxX() || y < bounds.minY() || y > bounds.maxY()) {
            throw new IllegalArgumentException(
                    "terrain shape coordinate outside world bounds: (" + x + ", " + y + ")");
        }
    }
}
