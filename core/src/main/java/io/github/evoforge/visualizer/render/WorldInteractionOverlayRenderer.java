package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.portal.ViewPortal;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalKind;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** World-space selection, move-target and portal interaction hints. */
public final class WorldInteractionOverlayRenderer {

    private static final Color SELECTED = new Color(0.97f, 0.84f, 0.31f, 0.96f);
    private static final Color MOVE = new Color(0.39f, 0.82f, 0.96f, 0.95f);
    private static final Color TARGET_OK = new Color(0.38f, 1.00f, 0.55f, 0.98f);
    private static final Color TARGET_BAD = new Color(1.00f, 0.35f, 0.30f, 0.98f);
    private static final Color TARGET_CHECK = new Color(1.00f, 0.84f, 0.32f, 0.98f);
    private static final Color PORTAL = new Color(0.86f, 0.92f, 0.79f, 0.95f);
    private static final Color PORTAL_DARK = new Color(0.08f, 0.11f, 0.09f, 0.92f);

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final SurfaceProjectionResolver surfaces;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private ViewPortalLookup portals = ViewPortalLookup.EMPTY;

    public WorldInteractionOverlayRenderer(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            SurfaceProjectionResolver surfaces) {
        if (view == null || state == null || camera == null || surfaces == null) {
            throw new IllegalArgumentException("interaction overlay dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
        this.surfaces = surfaces;
    }

    public void setPortals(ViewPortalLookup portals) {
        if (portals == null) throw new IllegalArgumentException("portals must not be null");
        this.portals = portals;
    }

    /** Draw before objects so an entrance/exit glyph never hides an occupant. */
    public void drawPortals(VisualizerCamera.VisibleRange range) {
        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        portals.forEach(portal -> drawPortalIfVisible(portal, range));
        shapes.end();
    }

    /** Draw after objects so selection and Move feedback remain easy to read. */
    public void drawFeedback() {
        shapes.setProjectionMatrix(camera.projection());

        // Cell interaction feedback deliberately occupies only the corners. Neither
        // persistent inspection nor transient Move hover may hide terrain/water art.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawSelectedCell();
        drawMovePreview();
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        ObjectId selected = state.selectedObject();
        if (selected != null && view.transforms().has(selected) && selectedVisible(selected)) {
            float x = view.transforms().x(selected) + 0.5f;
            float y = view.transforms().y(selected) + 0.5f;
            shapes.setColor(state.moveTargeting() ? MOVE : SELECTED);
            shapes.circle(x, y, 0.43f, 28);
            if (state.moveTargeting()) shapes.circle(x, y, 0.50f, 28);
        }
        shapes.end();
    }

    public void dispose() { shapes.dispose(); }

    private void drawSelectedCell() {
        VisualizerState.CellSelection selected = state.selectedCell();
        if (selected == null || !selectionVisible(selected)) return;
        drawCorners(selected.x(), selected.y(), SELECTED, 0.055f, 0.20f, 0.030f);
    }

    private void drawMovePreview() {
        VisualizerState.MoveTargetPreview preview = state.moveTargetPreview();
        if (!state.moveTargeting() || preview == null) return;

        Color color = switch (preview.status()) {
            case REACHABLE -> TARGET_OK;
            case BLOCKED -> TARGET_BAD;
            case CHECKING -> TARGET_CHECK;
        };
        drawCorners(preview.x(), preview.y(), color, 0.085f, 0.24f, 0.040f);
    }

    private void drawCorners(
            int cellX,
            int cellY,
            Color color,
            float inset,
            float arm,
            float thickness) {
        float left = cellX + inset;
        float right = cellX + 1f - inset;
        float bottom = cellY + inset;
        float top = cellY + 1f - inset;

        shapes.setColor(color);
        shapes.rect(left, bottom, arm, thickness);
        shapes.rect(left, bottom, thickness, arm);
        shapes.rect(right - arm, bottom, arm, thickness);
        shapes.rect(right - thickness, bottom, thickness, arm);
        shapes.rect(left, top - thickness, arm, thickness);
        shapes.rect(left, top - arm, thickness, arm);
        shapes.rect(right - arm, top - thickness, arm, thickness);
        shapes.rect(right - thickness, top - arm, thickness, arm);
    }

    private void drawPortalIfVisible(ViewPortal portal, VisualizerCamera.VisibleRange range) {
        int x;
        int y;
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            x = portal.surfaceX();
            y = portal.surfaceY();
        } else if (state.viewMode() == VisualizerViewMode.INTERIOR
                && state.interior() != null
                && state.interior().id().equals(portal.interior().id())) {
            x = portal.interiorX();
            y = portal.interiorY();
            if (portal.interiorZ() != state.selectedZ()) return;
        } else {
            return;
        }

        if (x < range.minX() || x > range.maxX() || y < range.minY() || y > range.maxY()) return;

        float cx = x + 0.5f;
        float cy = y + 0.5f;
        shapes.setColor(PORTAL_DARK);
        shapes.circle(cx, cy, 0.24f, 18);
        shapes.setColor(PORTAL);
        if (portal.kind() == ViewPortalKind.RAMP) {
            shapes.triangle(
                    cx - 0.13f, cy - 0.10f,
                    cx + 0.15f, cy,
                    cx - 0.13f, cy + 0.10f);
        } else {
            shapes.rect(cx - 0.11f, cy - 0.13f, 0.22f, 0.24f);
            shapes.setColor(PORTAL_DARK);
            shapes.rect(cx - 0.055f, cy - 0.13f, 0.11f, 0.15f);
        }
    }

    private boolean selectionVisible(VisualizerState.CellSelection selected) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            int standing = surfaces.standingZ(selected.x(), selected.y());
            return standing == SurfaceProjectionResolver.NO_Z || standing == selected.z();
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            return state.interior() != null
                    && selected.x() >= state.interior().minX()
                    && selected.x() <= state.interior().maxX()
                    && selected.y() >= state.interior().minY()
                    && selected.y() <= state.interior().maxY()
                    && selected.z() == state.selectedZ();
        }
        return selected.z() == state.selectedZ();
    }

    private boolean selectedVisible(ObjectId selected) {
        int x = view.transforms().x(selected);
        int y = view.transforms().y(selected);
        int z = view.transforms().z(selected);
        if (state.viewMode() == VisualizerViewMode.SURFACE) return surfaces.standingZ(x, y) == z;
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            return state.interior() != null
                    && state.interior().contains(x, y, z)
                    && z == state.selectedZ();
        }
        return z == state.selectedZ();
    }
}
