package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/** Presentation binding for one exact simulation Shape type. */
public interface ShapePresentation<S extends Shape> {

    TextureRegion terrainRegion(
            S shape,
            int topologyMask,
            int variant,
            boolean solidBody);

    /**
     * Decides whether the shared relief renderer should draw an outer edge on this Shape.
     *
     * <p>The default keeps the generic terrain rule: only a geometric discontinuity receives an
     * edge. Specialized Shape art may own some or all of its boundary presentation itself.</p>
     */
    default boolean reliefEdgeVisible(
            S shape,
            CellFace face,
            boolean boundaryAligned) {
        if (shape == null || face == null || face.dz() != 0) {
            throw new IllegalArgumentException("relief edge presentation requires a horizontal Shape face");
        }
        return !boundaryAligned;
    }

    default ShapeDirectionDiagnostic directionDiagnostic(
            S shape) {
        return ShapeDirectionDiagnostic.NONE;
    }

    String debugLabel(S shape);

    /** Releases presentation-owned resources; shared external atlases remain externally owned. */
    default void dispose() {
    }
}
