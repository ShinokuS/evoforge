package io.github.evoforge.visualizer.interaction;

import io.github.evoforge.visualizer.VisualizerState;

/** Immediate-mode debug panel hit model; renderer supplies content-derived metrics. */
public final class VisualizerDebugPanel {

    public static final float MARGIN = 12f;
    private static final Option[] OPTIONS = Option.values();

    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private float width = 1f;
    private float headerHeight = 1f;
    private float rowHeight = 1f;
    private float topInset;

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
    }

    /** Layout is measured from the active font by the renderer. */
    public void configureLayout(float width, float headerHeight, float rowHeight) {
        if (!Float.isFinite(width) || width <= 0f
                || !Float.isFinite(headerHeight) || headerHeight <= 0f
                || !Float.isFinite(rowHeight) || rowHeight <= 0f) {
            throw new IllegalArgumentException("debug panel layout metrics must be finite and > 0");
        }
        this.width = width;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight;
    }

    /** Reserves screen-space above this right-side panel for the inspector card. */
    public void setTopInset(float topInset) {
        if (!Float.isFinite(topInset) || topInset < 0f) {
            throw new IllegalArgumentException("topInset must be finite and >= 0");
        }
        this.topInset = topInset;
    }

    public float width() { return width; }
    public float headerHeight() { return headerHeight; }
    public float rowHeight() { return rowHeight; }

    public float x() {
        return Math.max(MARGIN, viewportWidth - width - MARGIN);
    }

    /** Top-left input coordinate, stacked below any top-right inspector reservation. */
    public float yTop() {
        float desired = MARGIN + topInset;
        float latestTop = Math.max(MARGIN, viewportHeight - height() - MARGIN);
        return Math.min(desired, latestTop);
    }

    public float height() {
        return headerHeight + rowHeight * OPTIONS.length;
    }

    public boolean contains(int screenX, int screenY) {
        return screenX >= x() && screenX < x() + width
                && screenY >= yTop() && screenY < yTop() + height();
    }

    public Option optionAt(int screenX, int screenY) {
        if (!contains(screenX, screenY)) return null;
        float rowsTop = yTop() + headerHeight;
        if (screenY < rowsTop) return null;
        int index = (int) ((screenY - rowsTop) / rowHeight);
        return index >= 0 && index < OPTIONS.length ? OPTIONS[index] : null;
    }

    public static Option option(int index) {
        if (index < 0 || index >= OPTIONS.length) {
            throw new IndexOutOfBoundsException("debug option index=" + index);
        }
        return OPTIONS[index];
    }

    public static int optionCount() { return OPTIONS.length; }

    public enum Option {
        GRID("Grid") {
            @Override public boolean checked(VisualizerState state) { return state.gridEnabled(); }
            @Override public void toggle(VisualizerState state) { state.toggleGridEnabled(); }
        },
        HEIGHT_CONTOURS("Height contours") {
            @Override public boolean checked(VisualizerState state) { return state.showHeightContours(); }
            @Override public void toggle(VisualizerState state) { state.toggleHeightContours(); }
        },
        ELEVATION_GRADIENT("Elevation gradient") {
            @Override public boolean checked(VisualizerState state) { return state.showElevationGradient(); }
            @Override public void toggle(VisualizerState state) { state.toggleElevationGradient(); }
        },
        ROUTE("Move route") {
            @Override public boolean checked(VisualizerState state) { return state.showRoute(); }
            @Override public void toggle(VisualizerState state) { state.toggleRoute(); }
        },
        TRANSITIONS("Transitions") {
            @Override public boolean checked(VisualizerState state) { return state.showTransitions(); }
            @Override public void toggle(VisualizerState state) { state.toggleTransitions(); }
        },
        SHAPE_DIRECTIONS("Shape directions") {
            @Override public boolean checked(VisualizerState state) { return state.showShapeDirections(); }
            @Override public void toggle(VisualizerState state) { state.toggleShapeDirections(); }
        },
        OCCUPANCY("Occupancy") {
            @Override public boolean checked(VisualizerState state) { return state.showOccupancy(); }
            @Override public void toggle(VisualizerState state) { state.toggleOccupancy(); }
        },
        VISION("Vision") {
            @Override public boolean checked(VisualizerState state) { return state.showVisionDiagnostics(); }
            @Override public void toggle(VisualizerState state) { state.toggleVisionDiagnostics(); }
        },
        TECHNICAL("Technical inspector") {
            @Override public boolean checked(VisualizerState state) { return state.showTechnicalDetails(); }
            @Override public void toggle(VisualizerState state) { state.toggleTechnicalDetails(); }
        };

        private final String label;

        Option(String label) { this.label = label; }
        public String label() { return label; }
        public abstract boolean checked(VisualizerState state);
        public abstract void toggle(VisualizerState state);
    }
}
