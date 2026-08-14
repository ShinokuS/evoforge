package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;

/** Generic developer overlay for the current production MoveTo route of the selected object. */
public final class MoveToRouteDiagnosticRenderer {
    private static final Color ROUTE = new Color(0.18f, 0.82f, 1f, 0.96f);
    private static final Color GOAL = new Color(0.98f, 0.86f, 0.22f, 1f);

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public MoveToRouteDiagnosticRenderer(SimulationView view, VisualizerState state, VisualizerCamera camera) {
        if (view == null || state == null || camera == null) {
            throw new IllegalArgumentException("MoveTo route diagnostic dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        ObjectId selected = state.selectedObject();
        if (selected == null) return;
        PathRoute route = view.moveTo().activeRoute(selected);
        if (route == null) return;

        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(ROUTE);
        for (int index = 0; index < route.size(); index++) {
            int x = route.x(index);
            int y = route.y(index);
            int z = route.z(index);
            if (z != state.selectedZ() || !inside(range, x, y)) continue;
            shapes.rect(x + 0.16f, y + 0.16f, 0.68f, 0.68f);
        }
        if (route.goalZ() == state.selectedZ() && inside(range, route.goalX(), route.goalY())) {
            shapes.setColor(GOAL);
            shapes.rect(route.goalX() + 0.08f, route.goalY() + 0.08f, 0.84f, 0.84f);
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private static boolean inside(VisualizerCamera.VisibleRange range, int x, int y) {
        return x >= range.minX() && x <= range.maxX() && y >= range.minY() && y <= range.maxY();
    }
}
