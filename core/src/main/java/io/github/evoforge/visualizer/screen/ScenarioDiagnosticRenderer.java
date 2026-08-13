package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarker;
import io.github.evoforge.visualizer.scenario.ScenarioCellMarkerStyle;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;

/** Draws generic presentation-only cell diagnostics for the selected standing Z. */
final class ScenarioDiagnosticRenderer {

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

            switch (marker.style()) {
                case ROUTE -> shapes.rect(
                        marker.x() + 0.34f,
                        marker.y() + 0.34f,
                        0.32f,
                        0.32f);
                case START, GOAL -> shapes.rect(
                        marker.x() + 0.18f,
                        marker.y() + 0.18f,
                        0.64f,
                        0.64f);
                case WARNING -> shapes.rect(
                        marker.x() + 0.08f,
                        marker.y() + 0.08f,
                        0.84f,
                        0.10f);
            }
        }

        shapes.end();
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
            shapes.rect(
                    marker.x() + 0.08f,
                    marker.y() + 0.08f,
                    0.84f,
                    0.84f);

            if (marker.style()
                    == ScenarioCellMarkerStyle.GOAL) {
                shapes.line(
                        marker.x() + 0.18f,
                        marker.y() + 0.18f,
                        marker.x() + 0.82f,
                        marker.y() + 0.82f);
                shapes.line(
                        marker.x() + 0.82f,
                        marker.y() + 0.18f,
                        marker.x() + 0.18f,
                        marker.y() + 0.82f);
            }
        }

        shapes.end();
    }

    void dispose() {
        shapes.dispose();
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
