package io.github.evoforge.visualizer;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;

/** Owns visualizer camera position, zoom and view-space conversion. */
public final class VisualizerCamera {

    private static final float BASE_VIEW_WIDTH = 28f;
    private static final float PAN_SPEED = 8f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 4f;
    private static final float FAR_ZOOM_SMOOTH_SAMPLING = 2.5f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Vector3 pick = new Vector3();

    private float targetX;
    private float targetY;
    private int screenWidth = 1;
    private int screenHeight = 1;

    public VisualizerCamera() {
        camera.position.set(0f, 0f, 0f);
    }

    public void resize(
            int width,
            int height) {

        if (width <= 0 || height <= 0) {
            return;
        }

        screenWidth = width;
        screenHeight = height;
        camera.viewportWidth = BASE_VIEW_WIDTH;
        camera.viewportHeight = BASE_VIEW_WIDTH
                * (float) height
                / (float) width;
        update();
    }

    public void pan(
            int directionX,
            int directionY,
            float deltaSeconds) {

        float distance = PAN_SPEED * camera.zoom * deltaSeconds;
        targetX += directionX * distance;
        targetY += directionY * distance;
    }

    public void zoom(
            float amountY) {

        camera.zoom = MathUtils.clamp(
                camera.zoom * (1f + amountY * 0.1f),
                MIN_ZOOM,
                MAX_ZOOM);
    }

    public void update() {
        camera.position.set(targetX, targetY, 0f);
        camera.update();
    }

    public Matrix4 projection() {
        return camera.combined;
    }

    public VisibleRange visibleRange() {
        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f;

        return new VisibleRange(
                MathUtils.floor(camera.position.x - halfWidth) - 1,
                MathUtils.ceil(camera.position.x + halfWidth) + 1,
                MathUtils.floor(camera.position.y - halfHeight) - 1,
                MathUtils.ceil(camera.position.y + halfHeight) + 1);
    }

    public Cell cellAt(
            int screenX,
            int screenY) {

        pick.set(screenX, screenY, 0f);
        camera.unproject(pick);
        return new Cell(
                MathUtils.floor(pick.x),
                MathUtils.floor(pick.y));
    }

    public float worldUnitsPerPixel() {
        float horizontal = camera.viewportWidth * camera.zoom
                / Math.max(1, screenWidth);
        float vertical = camera.viewportHeight * camera.zoom
                / Math.max(1, screenHeight);
        return Math.max(horizontal, vertical);
    }

    public boolean smoothLandscapeSampling() {
        return camera.zoom >= FAR_ZOOM_SMOOTH_SAMPLING;
    }

    public String zoomLabel() {
        return (Math.round(camera.zoom * 100f) / 100f) + "x";
    }

    public record VisibleRange(
            int minX,
            int maxX,
            int minY,
            int maxY) {
    }

    public record Cell(
            int x,
            int y) {
    }
}
