package io.github.evoforge.visualizer.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class VisualizerContextMenuTest {

    @Test
    void rowsUseMeasuredLayoutTopLeftCoordinatesAndClampToViewport() {
        VisualizerContextMenu menu = new VisualizerContextMenu();
        menu.configureLayout(146f, 34f, 32f);
        menu.open(
                790,
                590,
                800,
                600,
                "Object",
                VisualizerContextMenu.Action.MOVE,
                VisualizerContextMenu.Action.CANCEL_MOVE);

        assertTrue(menu.visible());
        assertEquals(146f, menu.width());
        assertTrue(menu.x() + menu.width() <= 792f);
        assertTrue(menu.yTop() + menu.height() <= 592f);

        int x = Math.round(menu.x() + 12f);
        int firstRowY = Math.round(
                menu.yTop()
                        + menu.headerHeight()
                        + menu.rowHeight() * 0.5f);
        assertEquals(
                VisualizerContextMenu.Action.MOVE,
                menu.actionAt(x, firstRowY));
        assertNull(menu.actionAt(0, 0));

        menu.close();
        assertFalse(menu.visible());
    }

    @Test
    void portalMenuCanExposePhysicalMoveHereSeparatelyFromViewTransition() {
        VisualizerContextMenu menu = new VisualizerContextMenu();
        menu.configureLayout(154f, 34f, 32f);
        menu.open(
                100,
                100,
                800,
                600,
                "Cave entrance",
                VisualizerContextMenu.Action.MOVE_HERE,
                VisualizerContextMenu.Action.RETURN_SURFACE);

        int x = Math.round(menu.x() + 12f);
        int moveHereY = Math.round(
                menu.yTop() + menu.headerHeight() + menu.rowHeight() * 0.5f);
        int returnY = Math.round(
                menu.yTop() + menu.headerHeight() + menu.rowHeight() * 1.5f);

        assertEquals(VisualizerContextMenu.Action.MOVE_HERE, menu.actionAt(x, moveHereY));
        assertEquals(VisualizerContextMenu.Action.RETURN_SURFACE, menu.actionAt(x, returnY));
    }
}
