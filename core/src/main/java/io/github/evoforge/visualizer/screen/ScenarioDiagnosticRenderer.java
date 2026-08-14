package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Draws presentation-only cell diagnostics for the selected standing Z. */
final class ScenarioDiagnosticRenderer {

    private static final float ENDPOINT_INSET = 0.01f;
    private static final float ENDPOINT_FRAME = 0.04f;

    private static final Color START =
            new Color(0.35f, 0.95f, 0.45f, 1f);
    private static final Color GOAL =
            new Color(0.95f, 0.35f, 0.35f, 1f);
    private static final Color ROUTE =
            new Color(1f, 0.85f, 0.2f, 1f);
    private static final Color WARNING =
            new Color(1f, 0.55f, 0.15f, 1f);

    private final ShapeRenderer shapes =
            new ShapeRenderer();

    void draw(
            ScenarioDiagnostics diagnostics,
            Matrix4 worldProjection,
            int selectedZ) {

        if (diagnostics.empty()) {
            return;
        }

        shapes.setProjectionMatrix(worldProjection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        for (int index = 0;
                index < diagnostics.cellCount();
                index++) {

            ScenarioCellMarker marker =
                    diagnostics.cell(index);
            if (marker.z() != selectedZ) {
                continue;
            }

            shapes.setColor(color(marker.style()));
            drawMarker(marker);
        }

        shapes.end();
    }

    void dispose() {
        shapes.dispose();
    }

    private void drawMarker(
            ScenarioCellMarker marker) {

        float x = marker.x();
        float y = marker.y();

        switch (marker.style()) {
            case ROUTE -> shapes.rect(
                    x + 0.34f,
                    y + 0.34f,
                    0.32f,
                    0.32f);
            case START, GOAL -> drawEndpointFrame(x, y);
            case WARNING -> {
                // Occupied route cell: keep the object's central footprint clear.
                shapes.rect(
                        x + 0.14f,
                        y + 0.78f,
                        0.16f,
                        0.04f);
                shapes.rect(
                        x + 0.14f,
                        y + 0.78f,
                        0.04f,
                        0.10f);
            }
        }
    }

    /**
     * Endpoint diagnostics own the outermost cell band. Occupancy keeps its
     * own inner frames, leaving the object footprint in the center untouched.
     */
    private void drawEndpointFrame(
            float x,
            float y) {

        float min = ENDPOINT_INSET;
        float max = 1f - ENDPOINT_INSET;
        float length = max - min;
        float innerMax = max - ENDPOINT_FRAME;

        shapes.rect(
                x + min,
                y + min,
                length,
                ENDPOINT_FRAME);
        shapes.rect(
                x + min,
                y + innerMax,
                length,
                ENDPOINT_FRAME);
        shapes.rect(
                x + min,
                y + min,
                ENDPOINT_FRAME,
                length);
        shapes.rect(
                x + innerMax,
                y + min,
                ENDPOINT_FRAME,
                length);
    }

    private static Color color(
            ScenarioCellMarkerStyle style) {

        return switch (style) {
            case START -> START;
            case GOAL -> GOAL;
            case ROUTE -> ROUTE;
            case WARNING -> WARNING;
        };
    }
}
