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
    private static final float DEFAULT_MAX_ZOOM = 4f;
    private static final float FAR_ZOOM_SMOOTH_SAMPLING = 2.5f;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final Vector3 pick = new Vector3();

    private float targetX;
    private float targetY;
    private float maximumZoom = DEFAULT_MAX_ZOOM;
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

    public void setView(
            float x,
            float y,
            float zoom) {

        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException(
                    "camera coordinates must be finite");
        }
        if (!Float.isFinite(zoom) || zoom <= 0f) {
            throw new IllegalArgumentException(
                    "zoom must be finite and > 0");
        }

        targetX = x;
        targetY = y;
        camera.zoom = MathUtils.clamp(zoom, MIN_ZOOM, maximumZoom);
        update();
    }

    /**
     * Fits one world-space rectangle while retaining the normal scenario zoom semantics.
     * Large inspection workspaces may therefore zoom farther out than ordinary scenarios
     * without changing the scenario camera defaults.
     */
    public void fitBounds(
            float minX,
            float maxX,
            float minY,
            float maxY,
            float paddingFactor) {

        if (!Float.isFinite(minX)
                || !Float.isFinite(maxX)
                || !Float.isFinite(minY)
                || !Float.isFinite(maxY)
                || maxX <= minX
                || maxY <= minY) {
            throw new IllegalArgumentException("camera fit bounds must be finite and non-empty");
        }
        if (!Float.isFinite(paddingFactor) || paddingFactor < 1f) {
            throw new IllegalArgumentException("camera fit padding must be finite and >= 1");
        }

        float fitZoom = Math.max(
                (maxX - minX) / Math.max(0.0001f, camera.viewportWidth),
                (maxY - minY) / Math.max(0.0001f, camera.viewportHeight))
                * paddingFactor;
        maximumZoom = Math.max(DEFAULT_MAX_ZOOM, fitZoom);
        setView(
                (minX + maxX) * 0.5f,
                (minY + maxY) * 0.5f,
                fitZoom);
    }

    public void pan(
            int directionX,
            int directionY,
            float deltaSeconds) {

        float distance = PAN_SPEED * camera.zoom * deltaSeconds;
        targetX += directionX * distance;
        targetY += directionY * distance;
    }

    /** Moves the camera target by an exact world-space delta. */
    public void panBy(
            float deltaX,
            float deltaY) {
        if (!Float.isFinite(deltaX) || !Float.isFinite(deltaY)) {
            throw new IllegalArgumentException("camera pan delta must be finite");
        }
        targetX += deltaX;
        targetY += deltaY;
    }

    /**
     * Keeps the visible viewport tied to a finite world rectangle.
     *
     * <p>When the current viewport is larger than the allowed rectangle on one axis, that axis is
     * centered instead of allowing the entire world to be panned off-screen.</p>
     */
    public void constrainToBounds(
            float minX,
            float maxX,
            float minY,
            float maxY,
            float margin) {
        if (!Float.isFinite(minX)
                || !Float.isFinite(maxX)
                || !Float.isFinite(minY)
                || !Float.isFinite(maxY)
                || !Float.isFinite(margin)
                || maxX <= minX
                || maxY <= minY
                || margin < 0f) {
            throw new IllegalArgumentException("camera bounds must be finite and non-empty");
        }

        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f;
        targetX = constrainedCenter(targetX, minX, maxX, halfWidth, margin);
        targetY = constrainedCenter(targetY, minY, maxY, halfHeight, margin);
    }

    public void zoom(
            float amountY) {

        camera.zoom = MathUtils.clamp(
                camera.zoom * (1f + amountY * 0.1f),
                MIN_ZOOM,
                maximumZoom);
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

        WorldPoint point = worldAt(screenX, screenY);
        return new Cell(
                MathUtils.floor(point.x()),
                MathUtils.floor(point.y()));
    }

    /** Precise world-space pick used when an interaction occupies only part of a cell. */
    public WorldPoint worldAt(
            int screenX,
            int screenY) {

        pick.set(screenX, screenY, 0f);
        camera.unproject(pick);
        return new WorldPoint(pick.x, pick.y);
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

    private static float constrainedCenter(
            float center,
            float minimum,
            float maximum,
            float halfViewport,
            float margin) {
        float lower = minimum - margin + halfViewport;
        float upper = maximum + margin - halfViewport;
        if (lower > upper) return (minimum + maximum) * 0.5f;
        return MathUtils.clamp(center, lower, upper);
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

    public record WorldPoint(
            float x,
            float y) {
    }
}
