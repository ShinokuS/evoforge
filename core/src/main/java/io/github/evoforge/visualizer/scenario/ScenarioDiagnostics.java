package io.github.evoforge.visualizer.scenario;

import io.github.evoforge.visualizer.presentation.route.RoutePresentation;

/** Immutable presentation-only diagnostics attached to one scenario session. */
public final class ScenarioDiagnostics {

    public static final ScenarioDiagnostics NONE =
            new ScenarioDiagnostics(new ScenarioCellMarker[0], RoutePresentation.EMPTY, "");

    private final ScenarioCellMarker[] cells;
    private final RoutePresentation route;
    private final String summary;

    public ScenarioDiagnostics(
            ScenarioCellMarker[] cells,
            String summary) {
        this(cells, RoutePresentation.EMPTY, summary);
    }

    public ScenarioDiagnostics(
            ScenarioCellMarker[] cells,
            RoutePresentation route,
            String summary) {
        if (cells == null || route == null || summary == null) {
            throw new IllegalArgumentException("diagnostics must not contain null");
        }
        this.cells = cells.clone();
        for (ScenarioCellMarker cell : this.cells) {
            if (cell == null) {
                throw new IllegalArgumentException("diagnostic cell must not be null");
            }
        }
        this.route = route;
        this.summary = summary;
    }

    public int cellCount() { return cells.length; }
    public ScenarioCellMarker cell(int index) { return cells[index]; }
    public RoutePresentation route() { return route; }
    public String summary() { return summary; }
    public boolean empty() { return cells.length == 0 && route.empty() && summary.isEmpty(); }
}
