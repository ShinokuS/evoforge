package io.github.evoforge.visualizer.interaction;

import java.util.Arrays;
import java.util.List;

/** Allocation-light screen-space context menu state shared by input and rendering. */
public final class VisualizerContextMenu {

    private boolean visible;
    private int anchorX;
    private int anchorY;
    private int viewportWidth = 1;
    private int viewportHeight = 1;
    private float width = 1f;
    private float headerHeight = 1f;
    private float rowHeight = 1f;
    private String title = "";
    private List<Action> actions = List.of();

    public boolean visible() { return visible; }
    public float x() {
        return clamp(anchorX + 8f, 8f, Math.max(8f, viewportWidth - width - 8f));
    }
    public float yTop() {
        return clamp(anchorY + 8f, 8f, Math.max(8f, viewportHeight - height() - 8f));
    }
    public float width() { return width; }
    public float headerHeight() { return headerHeight; }
    public float rowHeight() { return rowHeight; }
    public String title() { return title; }
    public List<Action> actions() { return actions; }
    public float height() { return headerHeight + rowHeight * actions.size(); }

    /** Renderer supplies dimensions measured from the active bitmap font/content. */
    public void configureLayout(float width, float headerHeight, float rowHeight) {
        if (!Float.isFinite(width) || width <= 0f
                || !Float.isFinite(headerHeight) || headerHeight <= 0f
                || !Float.isFinite(rowHeight) || rowHeight <= 0f) {
            throw new IllegalArgumentException("context menu layout metrics must be finite and > 0");
        }
        this.width = width;
        this.headerHeight = headerHeight;
        this.rowHeight = rowHeight;
    }

    public void open(
            int screenX,
            int screenY,
            int viewportWidth,
            int viewportHeight,
            String title,
            Action... actions) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (actions == null || actions.length == 0) {
            throw new IllegalArgumentException("context menu must contain actions");
        }
        this.title = title;
        this.actions = List.copyOf(Arrays.asList(actions));
        this.anchorX = screenX;
        this.anchorY = screenY;
        this.viewportWidth = Math.max(1, viewportWidth);
        this.viewportHeight = Math.max(1, viewportHeight);
        visible = true;
    }

    public void close() {
        visible = false;
        title = "";
        actions = List.of();
    }

    /** Input coordinates use LibGDX's top-left screen origin. */
    public Action actionAt(int screenX, int screenY) {
        if (!visible) return null;
        if (screenX < x() || screenX >= x() + width) return null;
        float rowsTop = yTop() + headerHeight;
        if (screenY < rowsTop || screenY >= yTop() + height()) return null;
        int index = (int) ((screenY - rowsTop) / rowHeight);
        return index >= 0 && index < actions.size() ? actions.get(index) : null;
    }

    public boolean contains(int screenX, int screenY) {
        return visible
                && screenX >= x() && screenX < x() + width
                && screenY >= yTop() && screenY < yTop() + height();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum Action {
        MOVE("Move"),
        MOVE_HERE("Move here"),
        CANCEL_MOVE("Cancel move"),
        ENTER("View inside"),
        RETURN_SURFACE("Return outside");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() { return label; }
    }
}
