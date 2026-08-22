package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.simulation.world.geometry.TransitionMask;
import io.github.evoforge.simulation.world.space.occupancy.OccupancyState;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.presentation.ShapeDirectionDiagnostic;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;

/** Explicit world-space diagnostics. Perspective and grid presentation live elsewhere. */
public final class VisualizerOverlayRenderer {

    private static final float DIAGNOSTIC_SHADOW_PIXELS = 5.0f;
    private static final float DIAGNOSTIC_STROKE_PIXELS = 2.75f;
    private static final float OCCUPANCY_STROKE_PIXELS = 3.0f;
    private static final float OCCUPIED_FRAME_INSET = 0.10f;
    private static final float RESERVED_FRAME_INSET = 0.20f;

    private static final Color DIAGNOSTIC_SHADOW =
            new Color(0.02f, 0.025f, 0.022f, 0.94f);
    private static final Color SHAPE_DIRECTION =
            new Color(1f, 0.78f, 0.08f, 1f);
    private static final Color OCCUPIED_CELL =
            new Color(1f, 0.20f, 0.52f, 1f);
    private static final Color RESERVED_CELL =
            new Color(1f, 0.72f, 0.10f, 1f);
    private static final Color TRANSITION_FLAT =
            new Color(0.10f, 1f, 0.92f, 1f);
    private static final Color TRANSITION_UP =
            new Color(0.52f, 1f, 0.18f, 1f);
    private static final Color TRANSITION_DOWN =
            new Color(1f, 0.38f, 0.10f, 1f);

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ShapePresentationRegistry shapePresentations;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public VisualizerOverlayRenderer(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera,
            io.github.evoforge.visualizer.visual.LandscapeSliceResolver ignoredSliceResolver,
            ShapePresentationRegistry shapePresentations) {

        this.view = require(view, "view");
        this.state = require(state, "state");
        this.camera = require(camera, "camera");
        require(ignoredSliceResolver, "sliceResolver");
        this.shapePresentations = require(shapePresentations, "shapePresentations");
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        if (!state.showOccupancy()
                && !state.showShapeDirections()
                && !state.showTransitions()) {
            return;
        }

        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (state.showOccupancy()) {
            drawOccupancyOverlay(range);
        }
        if (state.showShapeDirections()) {
            drawShapeDirections(range, state.selectedZ() - 1);
            drawShapeDirections(range, state.selectedZ());
        }
        if (state.showTransitions()) {
            drawTransitionOverlay();
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private void drawOccupancyOverlay(VisualizerCamera.VisibleRange range) {
        float thickness = camera.worldUnitsPerPixel() * OCCUPANCY_STROKE_PIXELS;

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                OccupancyState occupancyState = view.occupancy().state(
                        x,
                        y,
                        state.selectedZ());
                if (occupancyState == OccupancyState.FREE) continue;

                float inset = occupancyState == OccupancyState.OCCUPIED
                        ? OCCUPIED_FRAME_INSET
                        : RESERVED_FRAME_INSET;
                shapes.setColor(
                        occupancyState == OccupancyState.OCCUPIED
                                ? OCCUPIED_CELL
                                : RESERVED_CELL);
                drawCellFrame(x, y, inset, thickness);
            }
        }
    }

    private void drawCellFrame(
            int x,
            int y,
            float inset,
            float thickness) {

        float minX = x + inset;
        float minY = y + inset;
        float maxX = x + 1f - inset;
        float maxY = y + 1f - inset;

        shapes.rectLine(minX, minY, maxX, minY, thickness);
        shapes.rectLine(maxX, minY, maxX, maxY, thickness);
        shapes.rectLine(maxX, maxY, minX, maxY, thickness);
        shapes.rectLine(minX, maxY, minX, minY, thickness);
    }

    private void drawShapeDirections(
            VisualizerCamera.VisibleRange range,
            int terrainZ) {

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                Shape shape = view.geometry().find(x, y, terrainZ);
                ShapeDirectionDiagnostic diagnostic =
                        shapePresentations.directionDiagnostic(shape);
                if (!diagnostic.visible()) continue;
                drawDiagnosticArrow(
                        x,
                        y,
                        diagnostic.x(),
                        diagnostic.y(),
                        0.43f,
                        SHAPE_DIRECTION);
            }
        }
    }

    private void drawTransitionOverlay() {
        VisualizerState.CellSelection selected = state.selectedCell();
        if (selected == null) return;

        int mask = view.navigation().transitions(
                selected.x(),
                selected.y(),
                selected.z());

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    if (!TransitionMask.contains(mask, dx, dy, dz)) continue;

                    Color color = dz > 0
                            ? TRANSITION_UP
                            : dz < 0 ? TRANSITION_DOWN : TRANSITION_FLAT;
                    drawTransitionArrow(
                            selected.x(),
                            selected.y(),
                            dx,
                            dy,
                            dz,
                            color);
                }
            }
        }
    }

    private void drawTransitionArrow(
            int x,
            int y,
            int dx,
            int dy,
            int dz,
            Color color) {

        float startX = x + 0.5f;
        float startY = y + 0.5f;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy);

        if (magnitude == 0f) {
            float pixel = camera.worldUnitsPerPixel();
            float radius = dz > 0 ? 0.13f : 0.10f;
            shapes.setColor(DIAGNOSTIC_SHADOW);
            shapes.circle(startX, startY, radius + pixel * 2.2f, 20);
            shapes.setColor(color);
            shapes.circle(startX, startY, radius, 20);
            return;
        }

        float unitX = dx / magnitude;
        float unitY = dy / magnitude;
        float length = 0.43f;
        drawDoubleStrokeArrow(
                startX,
                startY,
                startX + unitX * length,
                startY + unitY * length,
                unitX,
                unitY,
                color,
                0.145f);
    }

    private void drawDiagnosticArrow(
            int cellX,
            int cellY,
            int dx,
            int dy,
            float length,
            Color color) {

        float startX = cellX + 0.5f;
        float startY = cellY + 0.5f;
        drawDoubleStrokeArrow(
                startX,
                startY,
                startX + dx * length,
                startY + dy * length,
                dx,
                dy,
                color,
                0.15f);
    }

    private void drawDoubleStrokeArrow(
            float startX,
            float startY,
            float endX,
            float endY,
            float unitX,
            float unitY,
            Color color,
            float headSize) {

        float pixel = camera.worldUnitsPerPixel();
        float shadowWidth = pixel * DIAGNOSTIC_SHADOW_PIXELS;
        float strokeWidth = pixel * DIAGNOSTIC_STROKE_PIXELS;

        shapes.setColor(DIAGNOSTIC_SHADOW);
        shapes.rectLine(startX, startY, endX, endY, shadowWidth);
        drawFilledArrowHead(
                endX,
                endY,
                unitX,
                unitY,
                headSize + pixel * 2.2f);

        shapes.setColor(color);
        shapes.rectLine(startX, startY, endX, endY, strokeWidth);
        drawFilledArrowHead(endX, endY, unitX, unitY, headSize);
    }

    private void drawFilledArrowHead(
            float endX,
            float endY,
            float unitX,
            float unitY,
            float size) {

        float perpendicularX = -unitY;
        float perpendicularY = unitX;
        float backX = endX - unitX * size;
        float backY = endY - unitY * size;
        float halfWidth = size * 0.72f;

        shapes.triangle(
                endX,
                endY,
                backX + perpendicularX * halfWidth,
                backY + perpendicularY * halfWidth,
                backX - perpendicularX * halfWidth,
                backY - perpendicularY * halfWidth);
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
