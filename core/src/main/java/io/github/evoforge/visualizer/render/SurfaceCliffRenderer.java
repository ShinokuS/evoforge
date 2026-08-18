package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.SurfaceBoundaryContinuity;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;

/** Optional two-tone height-contour overlay; ordinary relief lives in terrain sprites. */
public final class SurfaceCliffRenderer {

    private static final Color HIGH_CONTOUR = new Color(0.95f, 0.98f, 0.88f, 0.92f);
    private static final Color LOW_CONTOUR = new Color(0.025f, 0.030f, 0.028f, 0.82f);

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public SurfaceCliffRenderer(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera) {
        if (view == null || state == null || camera == null) {
            throw new IllegalArgumentException("cliff renderer dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        if (!state.showHeightContours()) return;

        float thickness = Math.max(camera.worldUnitsPerPixel() * 1.35f, 0.015f);
        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                if (!view.terrainSurfaces().hasColumn(x, y)) continue;
                int z = view.terrainSurfaces().topZ(x, y);
                Shape shape = view.geometry().find(x, y, z);
                drawEastTransition(x, y, z, shape, thickness);
                drawNorthTransition(x, y, z, shape, thickness);
            }
        }
        shapes.end();
    }

    public void dispose() { shapes.dispose(); }

    private void drawEastTransition(
            int x,
            int y,
            int z,
            Shape shape,
            float thickness) {
        boolean neighbour = view.terrainSurfaces().hasColumn(x + 1, y);
        if (neighbour) {
            int neighbourZ = view.terrainSurfaces().topZ(x + 1, y);
            Shape neighbourShape = view.geometry().find(x + 1, y, neighbourZ);
            if (SurfaceBoundaryContinuity.aligns(
                    shape,
                    z,
                    CellFace.POSITIVE_X,
                    neighbourShape,
                    neighbourZ)) {
                return;
            }
            if (z > neighbourZ) {
                drawVerticalPair(x + 1f, y, thickness, true);
            } else {
                drawVerticalPair(x + 1f, y, thickness, false);
            }
            return;
        }
        drawVerticalPair(x + 1f, y, thickness, true);
    }

    private void drawNorthTransition(
            int x,
            int y,
            int z,
            Shape shape,
            float thickness) {
        boolean neighbour = view.terrainSurfaces().hasColumn(x, y + 1);
        if (neighbour) {
            int neighbourZ = view.terrainSurfaces().topZ(x, y + 1);
            Shape neighbourShape = view.geometry().find(x, y + 1, neighbourZ);
            if (SurfaceBoundaryContinuity.aligns(
                    shape,
                    z,
                    CellFace.POSITIVE_Y,
                    neighbourShape,
                    neighbourZ)) {
                return;
            }
            if (z > neighbourZ) {
                drawHorizontalPair(x, y + 1f, thickness, true);
            } else {
                drawHorizontalPair(x, y + 1f, thickness, false);
            }
            return;
        }
        drawHorizontalPair(x, y + 1f, thickness, true);
    }

    /** @param highOnWest true when the cell left of the boundary is higher. */
    private void drawVerticalPair(
            float boundaryX,
            int y,
            float thickness,
            boolean highOnWest) {
        if (highOnWest) {
            shapes.setColor(HIGH_CONTOUR);
            shapes.rect(boundaryX - thickness * 2f, y, thickness, 1f);
            shapes.setColor(LOW_CONTOUR);
            shapes.rect(boundaryX, y, thickness, 1f);
        } else {
            shapes.setColor(LOW_CONTOUR);
            shapes.rect(boundaryX - thickness, y, thickness, 1f);
            shapes.setColor(HIGH_CONTOUR);
            shapes.rect(boundaryX + thickness, y, thickness, 1f);
        }
    }

    /** @param highOnSouth true when the cell below the boundary is higher. */
    private void drawHorizontalPair(
            int x,
            float boundaryY,
            float thickness,
            boolean highOnSouth) {
        if (highOnSouth) {
            shapes.setColor(HIGH_CONTOUR);
            shapes.rect(x, boundaryY - thickness * 2f, 1f, thickness);
            shapes.setColor(LOW_CONTOUR);
            shapes.rect(x, boundaryY, 1f, thickness);
        } else {
            shapes.setColor(LOW_CONTOUR);
            shapes.rect(x, boundaryY - thickness, 1f, thickness);
            shapes.setColor(HIGH_CONTOUR);
            shapes.rect(x, boundaryY + thickness, 1f, thickness);
        }
    }
}
