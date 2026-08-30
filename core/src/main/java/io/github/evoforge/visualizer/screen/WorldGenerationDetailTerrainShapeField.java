package io.github.evoforge.visualizer.screen;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.terrain.shape.TerrainShapeField;
import io.github.evoforge.visualizer.VisualizerCamera;

/**
 * Atomic shape-side adapter for cell-detail rendering.
 *
 * <p>Elevation and terrain shapes for large-world {@code LOD x1} are authored together by
 * {@link WorldGenerationExactDetailTiles}. This adapter deliberately performs no independent lazy
 * fitting and never calls the authoritative large-world shape field from the render thread. Until an
 * exact tile frame is ready it returns the stable baseline shape projection; once ready it exposes
 * the shape field paired with the exact elevation snapshot from that same immutable frame.</p>
 */
final class WorldGenerationDetailTerrainShapeField {
    private WorldGenerationDetailTerrainShapeField() {
    }

    static TerrainShapeField preload(
            TerrainShapeField authoritative,
            ElevationField presentationElevation,
            VisualizerCamera.VisibleRange visible,
            boolean authoritativeElevationReady) {
        if (authoritative == null || presentationElevation == null || visible == null) {
            throw new IllegalArgumentException(
                    "detail shape preload requires shape/elevation fields and visible range");
        }
        if (authoritativeElevationReady) {
            TerrainShapeField exact = WorldGenerationExactDetailTiles.shapesFor(presentationElevation);
            if (exact != null) return exact;
        }
        return TerrainShapeField.baseline(authoritative.bounds());
    }

    static void suspend(TerrainShapeField terrainShapes) {
        // Exact detail work is owned by the world-anchored tile cache and is safe to retain for reuse.
    }

    static void invalidate(TerrainShapeField terrainShapes) {
        // No shape-side state is owned here.
    }

    static void refinementEnabledForTests(boolean enabled) {
        WorldGenerationExactDetailTiles.enabledForTests(enabled);
    }
}
