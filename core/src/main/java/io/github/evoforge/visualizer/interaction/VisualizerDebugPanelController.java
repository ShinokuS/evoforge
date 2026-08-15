package io.github.evoforge.visualizer.interaction;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import io.github.evoforge.visualizer.VisualizerState;

/** Screen-space input for the visualizer debug checkbox panel. */
public final class VisualizerDebugPanelController extends InputAdapter {

    private final VisualizerState state;
    private final VisualizerDebugPanel panel;

    public VisualizerDebugPanelController(VisualizerState state, VisualizerDebugPanel panel) {
        if (state == null || panel == null) {
            throw new IllegalArgumentException("debug panel dependencies must not be null");
        }
        this.state = state;
        this.panel = panel;
    }

    public void resize(int width, int height) {
        panel.resize(width, height);
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode != Input.Keys.F1) return false;
        state.toggleDebugPanel();
        return true;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (!state.debugPanelVisible() || button != Input.Buttons.LEFT) return false;
        if (!panel.contains(screenX, screenY)) return false;
        VisualizerDebugPanel.Option option = panel.optionAt(screenX, screenY);
        if (option != null) option.toggle(state);
        return true;
    }
}
