package io.github.evoforge.visualizer.presentation.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class RoutePresentationTest {

    @Test
    void preservesOrderedXyzAndCopiesInputArrays() {
        int[] xs = {1, 2, 3};
        int[] ys = {4, 5, 6};
        int[] zs = {0, 1, 3};
        RoutePresentation route = RoutePresentation.of(xs, ys, zs);

        xs[1] = 99;
        ys[1] = 99;
        zs[1] = 99;

        assertEquals(3, route.size());
        assertEquals(1, route.x(0));
        assertEquals(2, route.x(1));
        assertEquals(5, route.y(1));
        assertEquals(1, route.z(1));
        assertEquals(3, route.z(2));
    }
}
