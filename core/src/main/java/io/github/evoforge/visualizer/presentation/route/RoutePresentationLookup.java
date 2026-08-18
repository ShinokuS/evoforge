package io.github.evoforge.visualizer.presentation.route;

/** Dynamic presentation-only route source. */
@FunctionalInterface
public interface RoutePresentationLookup {
    RoutePresentation current();

    RoutePresentationLookup NONE = () -> RoutePresentation.EMPTY;
}
