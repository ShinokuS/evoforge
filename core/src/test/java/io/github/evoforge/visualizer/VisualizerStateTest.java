package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.presentation.portal.InteriorView;

final class VisualizerStateTest {

    @Test
    void objectAndTerrainInspectionTabsFollowSelectionAndCanBeSwitchedExplicitly() {
        VisualizerState state = new VisualizerState();
        ObjectId object = ObjectId.of(2, 0);

        assertEquals(VisualizerState.InspectorTab.TERRAIN, state.inspectorTab());

        state.selectCell(3, 4, 1, object);
        assertEquals(VisualizerState.InspectorTab.OBJECT, state.inspectorTab());

        state.setInspectorTab(VisualizerState.InspectorTab.TERRAIN);
        assertEquals(VisualizerState.InspectorTab.TERRAIN, state.inspectorTab());
        assertSame(object, state.selectedObject(), "switching tabs must not discard object selection");

        state.setInspectorTab(VisualizerState.InspectorTab.OBJECT);
        assertEquals(VisualizerState.InspectorTab.OBJECT, state.inspectorTab());

        state.selectCell(5, 4, 1, null);
        assertEquals(VisualizerState.InspectorTab.TERRAIN, state.inspectorTab());
        assertNull(state.selectedObject());

        state.setInspectorTab(VisualizerState.InspectorTab.OBJECT);
        assertEquals(VisualizerState.InspectorTab.TERRAIN, state.inspectorTab(),
                "an empty cell cannot expose an object-only tab");
    }

    @Test
    void selectionAndMoveDraftSurviveViewTransitions() {
        VisualizerState state = new VisualizerState();
        ObjectId object = ObjectId.of(3, 1);
        InteriorView cave = new InteriorView(
                "cave", "Cave", 4, 8, -2, 2, 0, 1, 0);

        state.selectCell(1, 2, 0, object);
        state.beginMoveTargeting(object);
        state.setMoveTargetPreview(6, 1, 0, VisualizerState.MoveTargetStatus.REACHABLE);
        state.enterInterior(cave);

        assertEquals(VisualizerViewMode.INTERIOR, state.viewMode());
        assertSame(object, state.selectedObject());
        assertSame(object, state.moveTargetingObject());
        assertNull(state.moveTargetPreview(), "surface hover must not leak into interior view");
        assertEquals(0, state.selectedZ());

        state.selectZ(1);
        assertEquals(1, state.selectedZ());
        assertSame(object, state.selectedObject());
        assertTrue(state.moveTargeting());

        state.setMoveTargetPreview(7, 1, 1, VisualizerState.MoveTargetStatus.BLOCKED);
        state.leaveInterior();
        assertEquals(VisualizerViewMode.SURFACE, state.viewMode());
        assertSame(object, state.selectedObject());
        assertTrue(state.moveTargeting());
        assertNull(state.moveTargetPreview(), "interior hover must not leak back to surface");
    }

    @Test
    void inspectingDestinationCellDuringMoveDraftKeepsMoverSelected() {
        VisualizerState state = new VisualizerState();
        ObjectId mover = ObjectId.of(9, 0);
        state.selectCell(1, 1, 0, mover);
        state.beginMoveTargeting(mover);

        state.selectCell(4, 3, 0, null);

        assertEquals(new VisualizerState.CellSelection(4, 3, 0), state.selectedCell());
        assertSame(mover, state.selectedObject());
        assertSame(mover, state.moveTargetingObject());
        assertEquals(VisualizerState.InspectorTab.OBJECT, state.inspectorTab());

        state.finishMoveTargeting("");
        state.selectCell(5, 3, 0, null);
        assertNull(state.selectedObject(),
                "ordinary empty-cell selection should clear the object after targeting ends");
        assertEquals(VisualizerState.InspectorTab.TERRAIN, state.inspectorTab());
    }

    @Test
    void surfaceIgnoresGlobalZNavigationAndMoveDraftCancelsSeparatelyFromSelection() {
        VisualizerState state = new VisualizerState();
        ObjectId object = ObjectId.of(7, 0);
        state.setSelectedZ(5);
        state.selectCell(2, 3, 5, object);
        state.beginMoveTargeting(object);
        state.setMoveTargetPreview(3, 3, 5, VisualizerState.MoveTargetStatus.CHECKING);

        state.selectZ(1);
        assertEquals(5, state.selectedZ());

        state.cancelMoveTargeting();
        assertFalse(state.moveTargeting());
        assertNull(state.moveTargetPreview());
        assertSame(object, state.selectedObject());
        assertEquals("Move cancelled", state.interactionMessage());
    }

    @Test
    void finishingMoveTargetingClearsTransientPreviewAndCanClearStatusMessage() {
        VisualizerState state = new VisualizerState();
        ObjectId object = ObjectId.of(5, 0);
        state.beginMoveTargeting(object);
        state.setMoveTargetPreview(4, 2, 0, VisualizerState.MoveTargetStatus.REACHABLE);

        state.finishMoveTargeting("");

        assertFalse(state.moveTargeting());
        assertNull(state.moveTargetPreview());
        assertEquals("", state.interactionMessage());
        assertSame(object, state.selectedObject());
    }

    @Test
    void gridOverlayDefaultsOffAndSurvivesPerspectiveChangesIndependently() {
        VisualizerState state = new VisualizerState();
        InteriorView cave = new InteriorView(
                "cave", "Cave", 0, 4, 0, 4, 0, 1, 0);

        assertEquals(0, state.gridMode());
        state.cycleGridMode();
        assertEquals(1, state.gridMode());

        state.enterInterior(cave);
        assertEquals(VisualizerViewMode.INTERIOR, state.viewMode());
        assertEquals(1, state.gridMode());

        state.cycleGridMode();
        assertEquals(2, state.gridMode());
        state.leaveInterior();
        assertEquals(VisualizerViewMode.SURFACE, state.viewMode());
        assertEquals(2, state.gridMode());

        state.cycleGridMode();
        assertEquals(0, state.gridMode());
    }
}
