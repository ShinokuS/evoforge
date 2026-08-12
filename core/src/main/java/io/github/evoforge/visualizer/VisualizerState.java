package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Mutable presentation state owned by the visualizer, never by simulation. */
public final class VisualizerState {

    private static final int[] LOWER_DEPTH_OPTIONS = {0, 1, 4, 8};

    private int selectedZ = 1;
    private int gridMode = 1;
    private int lowerDepthIndex = 3;
    private boolean showTransitions;
    private boolean showShapeDirections;
    private CellSelection selectedCell;
    private ObjectId selectedObject;

    public int selectedZ() {
        return selectedZ;
    }

    public int gridMode() {
        return gridMode;
    }

    public int lowerDepth() {
        return LOWER_DEPTH_OPTIONS[lowerDepthIndex];
    }

    public boolean showTransitions() {
        return showTransitions;
    }

    public boolean showShapeDirections() {
        return showShapeDirections;
    }

    public CellSelection selectedCell() {
        return selectedCell;
    }

    public ObjectId selectedObject() {
        return selectedObject;
    }

    public void selectZ(
            int delta) {

        selectedZ += delta;
        clearSelection();
    }

    public void cycleGridMode() {
        gridMode = (gridMode + 1) % 3;
    }

    public void toggleTransitions() {
        showTransitions = !showTransitions;
    }

    public void toggleShapeDirections() {
        showShapeDirections = !showShapeDirections;
    }

    public void cycleLowerDepth() {
        lowerDepthIndex = (lowerDepthIndex + 1)
                % LOWER_DEPTH_OPTIONS.length;
    }

    public void selectCell(
            int x,
            int y,
            ObjectId objectId) {

        selectedCell = new CellSelection(x, y, selectedZ);
        selectedObject = objectId;
    }

    public void clearSelection() {
        selectedCell = null;
        selectedObject = null;
    }

    public record CellSelection(
            int x,
            int y,
            int z) {
    }
}
