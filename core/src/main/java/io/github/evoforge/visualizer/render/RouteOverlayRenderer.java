package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.presentation.route.RoutePresentation;
import io.github.evoforge.visualizer.presentation.route.RoutePresentationLookup;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;
import io.github.evoforge.visualizer.visual.WorldCellPresentationVisibility;

/** One projection-aware route painter shared by production MoveTo and scenario diagnostics. */
public final class RouteOverlayRenderer {
    private static final Color SHADOW = new Color(0.015f, 0.035f, 0.055f, 0.88f);
    private static final Color LOW = new Color(0.12f, 0.58f, 0.82f, 0.98f);
    private static final Color HIGH = new Color(0.46f, 0.94f, 1f, 0.98f);
    private static final Color Z_CHANGE = new Color(1f, 0.72f, 0.18f, 1f);
    private static final Color GOAL = new Color(1f, 0.88f, 0.24f, 1f);

    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final SurfaceProjectionResolver surfaces;
    private final RoutePresentationLookup selectedRoute;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Color routeColor = new Color();
    private RoutePresentationLookup scenarioRoute = RoutePresentationLookup.NONE;

    public RouteOverlayRenderer(
            VisualizerState state,
            VisualizerCamera camera,
            SurfaceProjectionResolver surfaces,
            RoutePresentationLookup selectedRoute) {
        if (state == null || camera == null || surfaces == null || selectedRoute == null) {
            throw new IllegalArgumentException("route overlay dependencies must not be null");
        }
        this.state = state;
        this.camera = camera;
        this.surfaces = surfaces;
        this.selectedRoute = selectedRoute;
    }

    public void setScenarioRoute(RoutePresentationLookup scenarioRoute) {
        if (scenarioRoute == null) {
            throw new IllegalArgumentException("scenarioRoute must not be null");
        }
        this.scenarioRoute = scenarioRoute;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        if (!state.showRoute()) return;
        RoutePresentation route = selectedRoute.current();
        if (route == null || route.empty()) route = scenarioRoute.current();
        if (route == null || route.empty()) return;

        int minZ = route.z(0);
        int maxZ = minZ;
        for (int index = 1; index < route.size(); index++) {
            minZ = Math.min(minZ, route.z(index));
            maxZ = Math.max(maxZ, route.z(index));
        }

        float pixel = camera.worldUnitsPerPixel();
        float width = Math.max(0.055f, pixel * 2.4f);
        float shadowWidth = width + Math.max(0.045f, pixel * 2f);
        float nodeRadius = Math.max(0.075f, pixel * 3.2f);

        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        // Dark underlay keeps one continuous route legible across all terrain shades.
        shapes.setColor(SHADOW);
        for (int index = 1; index < route.size(); index++) {
            if (!segmentVisible(route, index - 1, index)) continue;
            if (!segmentNear(range, route, index - 1, index)) continue;
            shapes.rectLine(
                    center(route.x(index - 1)), center(route.y(index - 1)),
                    center(route.x(index)), center(route.y(index)),
                    shadowWidth);
        }

        for (int index = 1; index < route.size(); index++) {
            if (!segmentVisible(route, index - 1, index)) continue;
            if (!segmentNear(range, route, index - 1, index)) continue;
            shapes.setColor(colorForZ((route.z(index - 1) + route.z(index)) * 0.5f, minZ, maxZ));
            shapes.rectLine(
                    center(route.x(index - 1)), center(route.y(index - 1)),
                    center(route.x(index)), center(route.y(index)),
                    width);
            if (route.z(index - 1) != route.z(index)) {
                shapes.setColor(Z_CHANGE);
                shapes.circle(
                        (center(route.x(index - 1)) + center(route.x(index))) * 0.5f,
                        (center(route.y(index - 1)) + center(route.y(index))) * 0.5f,
                        nodeRadius * 0.78f,
                        12);
            }
        }

        for (int index = 0; index < route.size(); index++) {
            if (!pointVisible(route, index) || !inside(range, route.x(index), route.y(index))) continue;
            shapes.setColor(colorForZ(route.z(index), minZ, maxZ));
            shapes.circle(center(route.x(index)), center(route.y(index)), nodeRadius, 12);
        }

        int goal = route.size() - 1;
        if (pointVisible(route, goal) && inside(range, route.x(goal), route.y(goal))) {
            shapes.setColor(GOAL);
            shapes.circle(center(route.x(goal)), center(route.y(goal)), nodeRadius * 1.55f, 16);
            shapes.setColor(colorForZ(route.z(goal), minZ, maxZ));
            shapes.circle(center(route.x(goal)), center(route.y(goal)), nodeRadius * 0.72f, 12);
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private boolean pointVisible(RoutePresentation route, int index) {
        return WorldCellPresentationVisibility.visible(
                state,
                surfaces,
                route.x(index),
                route.y(index),
                route.z(index));
    }

    private boolean segmentVisible(RoutePresentation route, int first, int second) {
        return pointVisible(route, first) && pointVisible(route, second);
    }

    private Color colorForZ(float z, int minZ, int maxZ) {
        float t = minZ == maxZ ? 0.5f : (z - minZ) / (maxZ - minZ);
        return routeColor.set(
                LOW.r + (HIGH.r - LOW.r) * t,
                LOW.g + (HIGH.g - LOW.g) * t,
                LOW.b + (HIGH.b - LOW.b) * t,
                LOW.a + (HIGH.a - LOW.a) * t);
    }

    private static boolean segmentNear(
            VisualizerCamera.VisibleRange range,
            RoutePresentation route,
            int first,
            int second) {
        return inside(range, route.x(first), route.y(first))
                || inside(range, route.x(second), route.y(second));
    }

    private static boolean inside(VisualizerCamera.VisibleRange range, int x, int y) {
        return x >= range.minX() && x <= range.maxX()
                && y >= range.minY() && y <= range.maxY();
    }

    private static float center(int coordinate) {
        return coordinate + 0.5f;
    }
}
