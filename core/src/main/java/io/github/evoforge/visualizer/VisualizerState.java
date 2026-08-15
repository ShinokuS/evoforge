package io.github.evoforge.visualizer;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.portal.InteriorView;

/** Mutable presentation state owned by the visualizer, never by simulation. */
public final class VisualizerState {

    private static final int[] LOWER_DEPTH_OPTIONS = {0, 1, 4, 8};

    private int selectedZ = 1;
    private int gridMode;
    private int lowerDepthIndex = 3;
    private boolean showHeightContours;
    private boolean showRoute = true;
    private boolean showTransitions;
    private boolean showShapeDirections;
    private boolean showOccupancy;
    private boolean showVisionDiagnostics;
    private boolean showTechnicalDetails;
    private boolean debugPanelVisible = true;

    private VisualizerViewMode viewMode = VisualizerViewMode.SURFACE;
    private InteriorView interior;
    private CellSelection selectedCell;
    private ObjectId selectedObject;
    private ObjectId moveTargetingObject;
    private MoveTargetPreview moveTargetPreview;
    private String interactionMessage = "";

    public int selectedZ() { return selectedZ; }
    public int gridMode() { return gridMode; }
    public boolean gridEnabled() { return gridMode != 0; }
    public int lowerDepth() { return LOWER_DEPTH_OPTIONS[lowerDepthIndex]; }
    public boolean showHeightContours() { return showHeightContours; }
    public boolean showRoute() { return showRoute; }
    public boolean showTransitions() { return showTransitions; }
    public boolean showShapeDirections() { return showShapeDirections; }
    public boolean showOccupancy() { return showOccupancy; }
    public boolean showVisionDiagnostics() { return showVisionDiagnostics; }
    public boolean showTechnicalDetails() { return showTechnicalDetails; }
    public boolean debugPanelVisible() { return debugPanelVisible; }
    public VisualizerViewMode viewMode() { return viewMode; }
    public InteriorView interior() { return interior; }
    public CellSelection selectedCell() { return selectedCell; }
    public ObjectId selectedObject() { return selectedObject; }
    public ObjectId moveTargetingObject() { return moveTargetingObject; }
    public boolean moveTargeting() { return moveTargetingObject != null; }
    public MoveTargetPreview moveTargetPreview() { return moveTargetPreview; }
    public String interactionMessage() { return interactionMessage; }

    /** Changes the inspected Z without altering global object selection. */
    public void setSelectedZ(int selectedZ) {
        this.selectedZ = clampToInterior(selectedZ);
    }

    /** Z navigation is meaningful only in explicit interior/slice views. */
    public void selectZ(int delta) {
        if (viewMode == VisualizerViewMode.SURFACE || delta == 0) return;
        long candidate = (long) selectedZ + delta;
        if (candidate < Integer.MIN_VALUE) candidate = Integer.MIN_VALUE;
        if (candidate > Integer.MAX_VALUE) candidate = Integer.MAX_VALUE;
        setSelectedZ((int) candidate);
    }

    public void enterInterior(InteriorView target) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        interior = target;
        selectedZ = target.initialZ();
        viewMode = VisualizerViewMode.INTERIOR;
        moveTargetPreview = null;
    }

    public void leaveInterior() {
        interior = null;
        viewMode = VisualizerViewMode.SURFACE;
        moveTargetPreview = null;
    }

    /** Development-only slice perspective; diagnostic overlays remain independent. */
    public void toggleDebugSlice() {
        if (viewMode == VisualizerViewMode.INTERIOR) return;
        viewMode = viewMode == VisualizerViewMode.DEBUG_SLICE
                ? VisualizerViewMode.SURFACE
                : VisualizerViewMode.DEBUG_SLICE;
        moveTargetPreview = null;
    }

    /** 0 = off, 1 = subtle, 2 = strong. */
    public void cycleGridMode() { gridMode = (gridMode + 1) % 3; }
    public void toggleGridEnabled() { gridMode = gridMode == 0 ? 1 : 0; }
    public void toggleHeightContours() { showHeightContours = !showHeightContours; }
    public void toggleRoute() { showRoute = !showRoute; }
    public void toggleTransitions() { showTransitions = !showTransitions; }
    public void toggleShapeDirections() { showShapeDirections = !showShapeDirections; }
    public void toggleOccupancy() { showOccupancy = !showOccupancy; }
    public void toggleVisionDiagnostics() { showVisionDiagnostics = !showVisionDiagnostics; }
    public void toggleTechnicalDetails() { showTechnicalDetails = !showTechnicalDetails; }
    public void toggleDebugPanel() { debugPanelVisible = !debugPanelVisible; }

    public void cycleLowerDepth() {
        lowerDepthIndex = (lowerDepthIndex + 1) % LOWER_DEPTH_OPTIONS.length;
    }

    public void selectCell(int x, int y, int z, ObjectId objectId) {
        selectedCell = new CellSelection(x, y, z);
        // While choosing a Move destination, cell inspection and mover selection are
        // independent. Clicking/previewing a destination must not lose the actor whose
        // command is being composed. Outside a Move draft, clicking empty terrain still
        // clears ordinary object selection as expected.
        if (objectId != null || moveTargetingObject == null) {
            selectedObject = objectId;
        }
    }

    /** Compatibility helper for old slice-oriented callers. */
    public void selectCell(int x, int y, ObjectId objectId) {
        selectCell(x, y, selectedZ, objectId);
    }

    public void selectObject(ObjectId objectId) {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        selectedObject = objectId;
    }

    public void beginMoveTargeting(ObjectId objectId) {
        if (objectId == null) throw new IllegalArgumentException("objectId must not be null");
        selectedObject = objectId;
        moveTargetingObject = objectId;
        moveTargetPreview = null;
        interactionMessage = "";
    }

    public void finishMoveTargeting(String message) {
        moveTargetingObject = null;
        moveTargetPreview = null;
        interactionMessage = message == null ? "" : message;
    }

    public void cancelMoveTargeting() {
        if (moveTargetingObject != null) {
            moveTargetingObject = null;
            moveTargetPreview = null;
            interactionMessage = "Move cancelled";
        }
    }

    public void setMoveTargetPreview(int x, int y, int z, MoveTargetStatus status) {
        if (status == null) throw new IllegalArgumentException("status must not be null");
        moveTargetPreview = new MoveTargetPreview(x, y, z, status);
    }

    public void clearMoveTargetPreview() { moveTargetPreview = null; }

    public void setInteractionMessage(String message) {
        interactionMessage = message == null ? "" : message;
    }

    public void clearSelection() {
        selectedCell = null;
        selectedObject = null;
        moveTargetingObject = null;
        moveTargetPreview = null;
    }

    private int clampToInterior(int z) {
        if (viewMode != VisualizerViewMode.INTERIOR || interior == null) return z;
        return Math.max(interior.minZ(), Math.min(interior.maxZ(), z));
    }

    public enum MoveTargetStatus { CHECKING, REACHABLE, BLOCKED }
    public record MoveTargetPreview(int x, int y, int z, MoveTargetStatus status) { }
    public record CellSelection(int x, int y, int z) { }
}
