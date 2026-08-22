package io.github.evoforge.visualizer.presentation.route;

import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;
import io.github.evoforge.visualizer.VisualizerState;

/** Presentation adapter for the active MoveTo route of the currently selected object. */
public final class SelectedMoveToRouteLookup implements RoutePresentationLookup {
    private final SimulationView view;
    private final VisualizerState state;
    private PathRoute cachedRoute;
    private RoutePresentation cachedPresentation = RoutePresentation.EMPTY;

    public SelectedMoveToRouteLookup(SimulationView view, VisualizerState state) {
        if (view == null || state == null) {
            throw new IllegalArgumentException("selected route dependencies must not be null");
        }
        this.view = view;
        this.state = state;
    }

    @Override
    public RoutePresentation current() {
        ObjectId selected = state.selectedObject();
        PathRoute route = selected == null ? null : view.moveTo().activeRoute(selected);
        if (route == null) {
            cachedRoute = null;
            cachedPresentation = RoutePresentation.EMPTY;
            return cachedPresentation;
        }
        if (route != cachedRoute) {
            cachedRoute = route;
            cachedPresentation = RoutePresentation.from(route);
        }
        return cachedPresentation;
    }
}
