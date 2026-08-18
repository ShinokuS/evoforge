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
     * Whether the shared generic relief pass may draw an edge on this Shape face.
     *
     * <p>This is presentation ownership only. It does not change physical boundary continuity.
     * Specialized Shape art may own its boundary appearance and therefore suppress the generic
     * earth-edge overlay. Renderers must consult both cells of a shared boundary so an ordinary
     * neighbour cannot redraw an edge that the specialized Shape intentionally owns.</p>
     */
    default boolean genericReliefEdgeAllowed(
            S shape,
            CellFace face) {
        if (shape == null || face == null || face.dz() != 0) {
            throw new IllegalArgumentException(
                    "relief edge presentation requires a horizontal Shape face");
        }
        return true;
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
