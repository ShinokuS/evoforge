package io.github.evoforge.visualizer.presentation.route;

import io.github.evoforge.simulation.world.navigation.pathfinding.PathRoute;

/** Immutable presentation snapshot of one ordered XYZ route, including its source. */
public final class RoutePresentation {
    public static final RoutePresentation EMPTY =
            new RoutePresentation(new int[0], new int[0], new int[0]);

    private final int[] xs;
    private final int[] ys;
    private final int[] zs;

    private RoutePresentation(int[] xs, int[] ys, int[] zs) {
        if (xs == null || ys == null || zs == null) {
            throw new IllegalArgumentException("route coordinate arrays must not be null");
        }
        if (xs.length != ys.length || xs.length != zs.length) {
            throw new IllegalArgumentException("route coordinate arrays must have equal length");
        }
        this.xs = xs.clone();
        this.ys = ys.clone();
        this.zs = zs.clone();
    }

    public static RoutePresentation of(int[] xs, int[] ys, int[] zs) {
        return xs.length == 0 ? EMPTY : new RoutePresentation(xs, ys, zs);
    }

    /** Converts simulation path facts without changing their order or dropping Z. */
    public static RoutePresentation from(PathRoute route) {
        if (route == null) return EMPTY;
        int size = route.size() + 1;
        int[] xs = new int[size];
        int[] ys = new int[size];
        int[] zs = new int[size];
        xs[0] = route.sourceX();
        ys[0] = route.sourceY();
        zs[0] = route.sourceZ();
        for (int index = 0; index < route.size(); index++) {
            xs[index + 1] = route.x(index);
            ys[index + 1] = route.y(index);
            zs[index + 1] = route.z(index);
        }
        return new RoutePresentation(xs, ys, zs);
    }

    public int size() { return xs.length; }
    public boolean empty() { return xs.length == 0; }
    public int x(int index) { return xs[index]; }
    public int y(int index) { return ys[index]; }
    public int z(int index) { return zs[index]; }
}
