package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Draws non-route presentation diagnostics through the active XYZ view projection. */
final class ScenarioDiagnosticRenderer {

    private static final float ENDPOINT_INSET = 0.01f;
    private static final float ENDPOINT_FRAME = 0.04f;

    private static final Color START =
            new Color(0.35f, 0.95f, 0.45f, 1f);
    private static final Color GOAL =
            new Color(0.95f, 0.35f, 0.35f, 1f);
    private static final Color WARNING =
            new Color(1f, 0.55f, 0.15f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();

    void draw(
            ScenarioDiagnostics diagnostics,
            Matrix4 worldProjection,
            CellVisibility visibility) {

        if (diagnostics.empty()) return;
        if (visibility == null) throw new IllegalArgumentException("visibility must not be null");

        shapes.setProjectionMatrix(worldProjection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        for (int index = 0; index < diagnostics.cellCount(); index++) {
            ScenarioCellMarker marker = diagnostics.cell(index);
            // ROUTE is retained as a compatibility marker role, but route drawing is owned
            // exclusively by RouteOverlayRenderer.
            if (marker.style() == ScenarioCellMarkerStyle.ROUTE) continue;
            if (!visibility.visible(marker.x(), marker.y(), marker.z())) continue;

            shapes.setColor(color(marker.style()));
            drawMarker(marker);
        }

        shapes.end();
    }

    void dispose() {
        shapes.dispose();
    }

    private void drawMarker(ScenarioCellMarker marker) {
        float x = marker.x();
        float y = marker.y();

        switch (marker.style()) {
            case START, GOAL -> drawEndpointFrame(x, y);
            case WARNING -> {
                // Occupied route cell: keep the object's central footprint clear.
                shapes.rect(x + 0.14f, y + 0.78f, 0.16f, 0.04f);
                shapes.rect(x + 0.14f, y + 0.78f, 0.04f, 0.10f);
            }
            case ROUTE -> { }
        }
    }

    /** Endpoint diagnostics own the outermost cell band while leaving the object center clear. */
    private void drawEndpointFrame(float x, float y) {
        float min = ENDPOINT_INSET;
        float max = 1f - ENDPOINT_INSET;
        float length = max - min;
        float innerMax = max - ENDPOINT_FRAME;

        shapes.rect(x + min, y + min, length, ENDPOINT_FRAME);
        shapes.rect(x + min, y + innerMax, length, ENDPOINT_FRAME);
        shapes.rect(x + min, y + min, ENDPOINT_FRAME, length);
        shapes.rect(x + innerMax, y + min, ENDPOINT_FRAME, length);
    }

    private static Color color(ScenarioCellMarkerStyle style) {
        return switch (style) {
            case START -> START;
            case GOAL -> GOAL;
            case WARNING -> WARNING;
            case ROUTE -> throw new IllegalArgumentException("route markers use RouteOverlayRenderer");
        };
    }

    @FunctionalInterface
    interface CellVisibility {
        boolean visible(int x, int y, int z);
    }
}
