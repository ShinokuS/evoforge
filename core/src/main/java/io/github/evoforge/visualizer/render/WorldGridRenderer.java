package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;

/** Optional camera-local grid, independent from Surface/Interior/Slice perspective. */
public final class WorldGridRenderer {

    private static final Color SUBTLE = new Color(0.18f, 0.23f, 0.21f, 0.52f);
    private static final Color STRONG = new Color(0.39f, 0.47f, 0.43f, 0.78f);

    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public WorldGridRenderer(VisualizerState state, VisualizerCamera camera) {
        if (state == null || camera == null) {
            throw new IllegalArgumentException("grid renderer dependencies must not be null");
        }
        this.state = state;
        this.camera = camera;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        if (!state.gridEnabled()) return;

        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(state.gridMode() == 2 ? STRONG : SUBTLE);
        for (int x = range.minX(); x <= range.maxX() + 1; x++) {
            shapes.line(x, range.minY(), x, range.maxY() + 1f);
        }
        for (int y = range.minY(); y <= range.maxY() + 1; y++) {
            shapes.line(range.minX(), y, range.maxX() + 1f, y);
        }
        shapes.end();
    }

    public void dispose() { shapes.dispose(); }
}
