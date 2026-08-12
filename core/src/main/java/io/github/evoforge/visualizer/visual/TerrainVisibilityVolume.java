package io.github.evoforge.visualizer.visual;

import io.github.evoforge.simulation.runtime.SimulationView;

/** Current visibility-volume adapter: authoritative terrain is solid/opaque. */
public final class TerrainVisibilityVolume implements VisibilityVolumeLookup {

    private final SimulationView view;

    public TerrainVisibilityVolume(
            SimulationView view) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        this.view = view;
    }

    @Override
    public boolean solid(
            int x,
            int y,
            int z) {

        return view.terrain().contains(x, y, z);
    }

    @Override
    public boolean opaque(
            int x,
            int y,
            int z) {

        return solid(x, y, z);
    }

    @Override
    public boolean empty() {
        return view.terrainExtents().empty();
    }

    @Override
    public int minOccupiedZ() {
        return view.terrainExtents().minZ();
    }

    @Override
    public int maxOccupiedZ() {
        return view.terrainExtents().maxZ();
    }
}
