package io.github.evoforge.visualizer.scenario;

/** Immutable presentation-only diagnostics attached to one scenario session. */
public final class ScenarioDiagnostics {

    public static final ScenarioDiagnostics NONE =
            new ScenarioDiagnostics(new ScenarioCellMarker[0], "");

    private final ScenarioCellMarker[] cells;
    private final String summary;

    public ScenarioDiagnostics(
            ScenarioCellMarker[] cells,
            String summary) {
        if (cells == null || summary == null) {
            throw new IllegalArgumentException("diagnostics must not contain null");
        }
        this.cells = cells.clone();
        for (ScenarioCellMarker cell : this.cells) {
            if (cell == null) {
                throw new IllegalArgumentException("diagnostic cell must not be null");
            }
        }
        this.summary = summary;
    }

    public int cellCount() { return cells.length; }
    public ScenarioCellMarker cell(int index) { return cells[index]; }
    public String summary() { return summary; }
    public boolean empty() { return cells.length == 0 && summary.isEmpty(); }
}
