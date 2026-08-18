package io.github.evoforge.visualizer.interaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.evoforge.visualizer.VisualizerState;

final class VisualizerDebugPanelTest {

    @Test
    void panelRowsMapToIndependentOverlayToggles() {
        VisualizerState state = new VisualizerState();
        VisualizerDebugPanel panel = new VisualizerDebugPanel();
        panel.configureLayout(196f, 34f, 31f);
        panel.resize(1200, 800);

        assertTrue(state.showRoute(), "route overlay should remain visible by default");
        assertTrue(state.showElevationGradient(), "surface height color should be visible by default");
        assertFalse(state.gridEnabled());
        assertFalse(state.showOccupancy());

        int x = Math.round(panel.x() + 30f);
        int gridY = Math.round(panel.yTop()
                + panel.headerHeight()
                + panel.rowHeight() * 0.5f);
        assertEquals(VisualizerDebugPanel.Option.GRID, panel.optionAt(x, gridY));
        panel.optionAt(x, gridY).toggle(state);
        assertTrue(state.gridEnabled());

        int gradientIndex = VisualizerDebugPanel.Option.ELEVATION_GRADIENT.ordinal();
        int gradientY = Math.round(panel.yTop()
                + panel.headerHeight()
                + panel.rowHeight() * (gradientIndex + 0.5f));
        assertEquals(VisualizerDebugPanel.Option.ELEVATION_GRADIENT, panel.optionAt(x, gradientY));
        panel.optionAt(x, gradientY).toggle(state);
        assertFalse(state.showElevationGradient());

        int occupancyIndex = VisualizerDebugPanel.Option.OCCUPANCY.ordinal();
        int occupancyY = Math.round(panel.yTop()
                + panel.headerHeight()
                + panel.rowHeight() * (occupancyIndex + 0.5f));
        assertEquals(VisualizerDebugPanel.Option.OCCUPANCY, panel.optionAt(x, occupancyY));
        panel.optionAt(x, occupancyY).toggle(state);
        assertTrue(state.showOccupancy());
        assertTrue(state.gridEnabled(), "occupancy must not alter grid state");
        assertFalse(state.showElevationGradient(), "occupancy must not alter elevation gradient state");
    }

    @Test
    void topInsetStacksPanelBelowInspectorAndStillClampsToViewport() {
        VisualizerDebugPanel panel = new VisualizerDebugPanel();
        panel.configureLayout(196f, 34f, 31f);
        panel.resize(1200, 800);

        panel.setTopInset(180f);
        assertEquals(VisualizerDebugPanel.MARGIN + 180f, panel.yTop());

        panel.setTopInset(700f);
        float latestTop = 800f - panel.height() - VisualizerDebugPanel.MARGIN;
        assertEquals(latestTop, panel.yTop());
        assertTrue(panel.yTop() + panel.height() <= 800f - VisualizerDebugPanel.MARGIN + 0.001f);
    }
}
