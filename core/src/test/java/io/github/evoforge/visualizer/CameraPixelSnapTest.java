package io.github.evoforge.visualizer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

final class CameraPixelSnapTest {

    @Test
    void keepsRenderedViewportEdgeOnScreenPixelGrid() {
        float visibleWorld = 112f;
        int pixels = 1920;
        float target = 3.217f;

        float snapped = CameraPixelSnap.axis(target, visibleWorld, pixels);
        double worldPerPixel = (double) visibleWorld / pixels;
        double visibleMin = snapped - visibleWorld * 0.5;

        assertEquals(
                Math.rint(visibleMin / worldPerPixel),
                visibleMin / worldPerPixel,
                1.0e-4);
    }

    @Test
    void subPixelTargetMotionDoesNotMoveRenderedCamera() {
        float visibleWorld = 112f;
        int pixels = 1920;
        float pixel = visibleWorld / pixels;

        float first = CameraPixelSnap.axis(0f, visibleWorld, pixels);
        float second = CameraPixelSnap.axis(
                pixel * 0.20f,
                visibleWorld,
                pixels);

        assertEquals(first, second, 1.0e-6f);
    }

    @Test
    void crossingPixelBoundaryMovesExactlyOneScreenPixel() {
        float visibleWorld = 112f;
        int pixels = 1920;
        float pixel = visibleWorld / pixels;

        float first = CameraPixelSnap.axis(0f, visibleWorld, pixels);
        float second = CameraPixelSnap.axis(
                pixel * 0.75f,
                visibleWorld,
                pixels);

        assertEquals(pixel, second - first, 1.0e-5f);
    }

    @Test
    void rejectsInvalidViewport() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CameraPixelSnap.axis(0f, 0f, 1920));
        assertThrows(
                IllegalArgumentException.class,
                () -> CameraPixelSnap.axis(0f, 10f, 0));
    }
}
