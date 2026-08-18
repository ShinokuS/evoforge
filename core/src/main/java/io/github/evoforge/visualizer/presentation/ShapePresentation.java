package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;

/** Presentation binding for one exact simulation Shape type. */
public interface ShapePresentation<S extends Shape> {

    TextureRegion terrainRegion(
            S shape,
            int topologyMask,
            int variant,
            boolean solidBody);

    default ShapeDirectionDiagnostic directionDiagnostic(
            S shape) {
        return ShapeDirectionDiagnostic.NONE;
    }

    String debugLabel(S shape);

    /** Releases presentation-owned resources; shared external atlases remain externally owned. */
    default void dispose() {
    }
}
