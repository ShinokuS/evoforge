package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.pathfinding.PathRoute;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Generic developer overlay for the current production MoveTo route of the selected object. */
public final class MoveToRouteDiagnosticRenderer {
    private static final Color ROUTE = new Color(0.18f, 0.82f, 1f, 0.96f);
    private static final Color GOAL = new Color(0.98f, 0.86f, 0.22f, 1f);

    private final SimulationView view;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final SurfaceProjectionResolver surfaces;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public MoveToRouteDiagnosticRenderer(
            SimulationView view,
            VisualizerState state,
            VisualizerCamera camera) {
        if (view == null || state == null || camera == null) {
            throw new IllegalArgumentException("MoveTo route diagnostic dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.camera = camera;
        surfaces = new SurfaceProjectionResolver(view);
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        if (!state.showRoute()) return;
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
            if (!inside(range, x, y) || !visibleRouteCell(x, y, z)) continue;
            shapes.rect(x + 0.16f, y + 0.16f, 0.68f, 0.68f);
        }
        if (inside(range, route.goalX(), route.goalY())
                && visibleRouteCell(route.goalX(), route.goalY(), route.goalZ())) {
            shapes.setColor(GOAL);
            shapes.rect(route.goalX() + 0.08f, route.goalY() + 0.08f, 0.84f, 0.84f);
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private boolean visibleRouteCell(int x, int y, int z) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            return surfaces.standingZ(x, y) == z;
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            return state.interior() != null
                    && state.interior().contains(x, y, z)
                    && state.selectedZ() == z;
        }
        return state.selectedZ() == z;
    }

    private static boolean inside(VisualizerCamera.VisibleRange range, int x, int y) {
        return x >= range.minX() && x <= range.maxX() && y >= range.minY() && y <= range.maxY();
    }
}
