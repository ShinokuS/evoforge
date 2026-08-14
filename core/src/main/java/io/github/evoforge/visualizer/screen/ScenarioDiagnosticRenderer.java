package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Draws minimal presentation-only cell diagnostics for the selected standing Z. */
final class ScenarioDiagnosticRenderer {

    private static final Color START =
            new Color(0.35f, 0.95f, 0.45f, 0.9f);
    private static final Color GOAL =
            new Color(0.95f, 0.35f, 0.35f, 0.9f);
    private static final Color ROUTE =
            new Color(1f, 0.85f, 0.2f, 0.85f);
    private static final Color WARNING =
            new Color(1f, 0.55f, 0.15f, 0.9f);

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
        shapes.begin(ShapeRenderer.ShapeType.Line);

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
            case ROUTE -> shapes.line(
                    x + 0.38f,
                    y + 0.08f,
                    x + 0.62f,
                    y + 0.08f);
            case START -> {
                shapes.line(
                        x + 0.08f,
                        y + 0.08f,
                        x + 0.30f,
                        y + 0.08f);
                shapes.line(
                        x + 0.08f,
                        y + 0.08f,
                        x + 0.08f,
                        y + 0.30f);
            }
            case GOAL -> {
                shapes.line(
                        x + 0.70f,
                        y + 0.92f,
                        x + 0.92f,
                        y + 0.92f);
                shapes.line(
                        x + 0.92f,
                        y + 0.70f,
                        x + 0.92f,
                        y + 0.92f);
            }
            case WARNING -> {
                shapes.line(
                        x + 0.70f,
                        y + 0.08f,
                        x + 0.92f,
                        y + 0.08f);
                shapes.line(
                        x + 0.92f,
                        y + 0.08f,
                        x + 0.92f,
                        y + 0.30f);
            }
        }
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
