package io.github.evoforge.visualizer.interaction;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.render.VisualizerPrimaryHudRenderer;

/** Screen-space input for the selected object/terrain inspector tabs. */
public final class VisualizerPrimaryHudController extends InputAdapter {
    private final VisualizerState state;
    private final VisualizerPrimaryHudRenderer renderer;

    public VisualizerPrimaryHudController(
            VisualizerState state,
            VisualizerPrimaryHudRenderer renderer) {
        if (state == null || renderer == null) {
            throw new IllegalArgumentException("primary HUD input dependencies must not be null");
        }
        this.state = state;
        this.renderer = renderer;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Input.Buttons.LEFT) return false;
        VisualizerState.InspectorTab tab = renderer.tabAt(screenX, screenY);
        if (tab == null) return false;
        state.setInspectorTab(tab);
        return true;
    }
}
