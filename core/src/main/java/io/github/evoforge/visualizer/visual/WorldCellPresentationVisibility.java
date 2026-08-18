package io.github.evoforge.visualizer.visual;

import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;

/** Shared projection law for XYZ diagnostics in surface, slice and interior views. */
public final class WorldCellPresentationVisibility {
    private WorldCellPresentationVisibility() { }

    public static boolean visible(
            VisualizerState state,
            SurfaceProjectionResolver surfaces,
            int x,
            int y,
            int z) {
        if (state == null || surfaces == null) {
            throw new IllegalArgumentException("cell visibility dependencies must not be null");
        }
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            return surfaces.standingZ(x, y) == z;
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            return state.interior() != null
                    && state.interior().contains(x, y, z)
                    && state.selectedZ() == z;
        }
        return state.selectedZ() == z;
    }
}
