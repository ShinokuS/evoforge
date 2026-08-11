package io.github.evoforge;

import com.badlogic.gdx.ApplicationAdapter;
import io.github.evoforge.visualizer.VisualizerDemoWorld;
import io.github.evoforge.visualizer.ZLevelVisualizer;

/** Launches the minimal live simulation debug visualizer. */
public final class Main extends ApplicationAdapter {

    private ZLevelVisualizer visualizer;

    @Override
    public void create() {
        visualizer = new ZLevelVisualizer(
                VisualizerDemoWorld.create());
    }

    @Override
    public void render() {
        visualizer.render();
    }

    @Override
    public void resize(
            int width,
            int height) {

        if (visualizer != null) {
            visualizer.resize(
                    width,
                    height);
        }
    }

    @Override
    public void dispose() {
        if (visualizer != null) {
            visualizer.dispose();
        }
    }
}
