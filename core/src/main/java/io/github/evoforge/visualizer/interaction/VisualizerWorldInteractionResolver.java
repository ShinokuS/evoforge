package io.github.evoforge.visualizer.interaction;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Resolves presentation-mode coordinates into interactable authoritative world targets. */
final class VisualizerWorldInteractionResolver {
    private final SimulationView view;
    private final VisualizerState state;
    private final SurfaceProjectionResolver surfaces;
    private final LandscapeSliceResolver slices;
    private ViewPortalLookup portals = ViewPortalLookup.EMPTY;

    VisualizerWorldInteractionResolver(
            SimulationView view,
            VisualizerState state,
            SurfaceProjectionResolver surfaces,
            LandscapeSliceResolver slices) {
        if (view == null || state == null || surfaces == null || slices == null) {
            throw new IllegalArgumentException("world interaction resolver dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.surfaces = surfaces;
        this.slices = slices;
    }

    void setPortals(ViewPortalLookup portals) {
        if (portals == null) throw new IllegalArgumentException("portals must not be null");
        this.portals = portals;
    }

    ViewPortalLookup portals() {
        return portals;
    }

    ViewPortal portalAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            return portals.surfaceAt(x, y);
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR && state.interior() != null) {
            return portals.interiorAt(state.interior().id(), x, y, state.selectedZ());
        }
        return null;
    }

    ObjectId visibleObjectAt(int x, int y) {
        WorldInteractionTarget target = visibleTargetAt(x, y);
        if (target == null) return null;
        int count = view.cells().objectCount(x, y, target.z());
        if (count <= 0) return null;

        ObjectId selected = state.selectedObject();
        VisualizerState.CellSelection selectedCell = state.selectedCell();
        int selectedIndex = -1;
        if (selected != null
                && selectedCell != null
                && selectedCell.x() == x
                && selectedCell.y() == y
                && selectedCell.z() == target.z()) {
            for (int index = 0; index < count; index++) {
                if (selected.equals(view.cells().objectAt(x, y, target.z(), index))) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        return view.cells().objectAt(x, y, target.z(), (selectedIndex + 1) % count);
    }

    WorldInteractionTarget visibleTargetAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            int z = surfaces.standingZ(x, y);
            return z == SurfaceProjectionResolver.NO_Z
                    ? null
                    : new WorldInteractionTarget(x, y, z);
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            if (state.interior() == null
                    || x < state.interior().minX()
                    || x > state.interior().maxX()
                    || y < state.interior().minY()
                    || y > state.interior().maxY()) {
                return null;
            }
        }

        LandscapeSliceResolver.Cell slice = slices.resolve(
                x,
                y,
                state.selectedZ(),
                state.lowerDepth());
        return switch (slice.kind()) {
            case CURRENT_SURFACE -> new WorldInteractionTarget(x, y, state.selectedZ());
            case LOWER_SURFACE -> new WorldInteractionTarget(
                    x,
                    y,
                    safeStandingZ(slice.terrainZ()));
            case SOLID_BODY, EMPTY -> null;
        };
    }

    int selectionZAt(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            int standing = surfaces.standingZ(x, y);
            return standing == SurfaceProjectionResolver.NO_Z ? state.selectedZ() : standing;
        }
        return state.selectedZ();
    }

    private static int safeStandingZ(int terrainZ) {
        return terrainZ == Integer.MAX_VALUE ? Integer.MAX_VALUE : terrainZ + 1;
    }
}
