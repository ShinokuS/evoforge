package io.github.evoforge.visualizer.continuum;

import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.glutils.ImmediateModeRenderer20;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPage;

/**
 * Minimal real 3D observer for a bounded window of the authoritative Stage 6 surface.
 *
 * <p>LOD changes only the integer sampling step of the same surface. The 65x65 lattice is nested:
 * halving the step retains all shared coarse coordinates and adds samples between them. Camera and
 * presentation state never participate in Terrain generation.</p>
 */
public final class TerrainSurface3DInspector implements AutoCloseable {
    public static final int SAMPLE_SIDE = 65;
    public static final int DEFAULT_SAMPLE_STEP = 8_192;
    public static final int MIN_SAMPLE_STEP = 128;
    public static final int MAX_SAMPLE_STEP = 65_536;
    public static final float VERTICAL_EXAGGERATION = 32.0f;

    private static final int HALF_SAMPLES = (SAMPLE_SIDE - 1) / 2;
    private static final float LOCAL_WIDTH = 10.0f;
    private static final float CAMERA_DISTANCE = 12.5f;
    private static final float MIN_PITCH_DEGREES = 12.0f;
    private static final float MAX_PITCH_DEGREES = 78.0f;

    private final ContinuumMapInspectorModel model;
    private final PerspectiveCamera camera = new PerspectiveCamera(55.0f, 1.0f, 1.0f);
    private final ImmediateModeRenderer20 renderer =
            new ImmediateModeRenderer20((SAMPLE_SIDE - 1) * (SAMPLE_SIDE - 1) * 6, false, true, 0);
    private final float[] surfaceZ = new float[SAMPLE_SIDE * SAMPLE_SIDE];
    private final float[] localHeight = new float[SAMPLE_SIDE * SAMPLE_SIDE];
    private final float[] shade = new float[SAMPLE_SIDE * SAMPLE_SIDE];

    private long centerX;
    private long centerY;
    private int sampleStep = DEFAULT_SAMPLE_STEP;
    private float yawDegrees = 42.0f;
    private float pitchDegrees = 38.0f;
    private long sampledSourceRevision = Long.MIN_VALUE;
    private boolean dirty = true;

    public TerrainSurface3DInspector(ContinuumMapInspectorModel model) {
        if (model == null) throw new IllegalArgumentException("model must not be null");
        this.model = model;
        camera.near = 0.1f;
        camera.far = 100.0f;
        centerOn(Math.round(model.centerX()), Math.round(model.centerY()));
    }

    public long centerX() {
        return centerX;
    }

    public long centerY() {
        return centerY;
    }

    public int sampleStep() {
        return sampleStep;
    }

    public long sampledWorldSpan() {
        return (long) (SAMPLE_SIDE - 1) * sampleStep;
    }

    public void centerOn(long worldX, long worldY) {
        centerX = alignedClampedCenter(worldX, model.worldWidth());
        centerY = alignedClampedCenter(worldY, model.worldHeight());
        dirty = true;
    }

    public void orbitPixels(int deltaX, int deltaY) {
        yawDegrees = wrapDegrees(yawDegrees + deltaX * 0.35f);
        pitchDegrees = clamp(pitchDegrees + deltaY * 0.25f, MIN_PITCH_DEGREES, MAX_PITCH_DEGREES);
    }

    public void panPixels(int deltaX, int deltaY, int viewportWidth, int viewportHeight) {
        long span = sampledWorldSpan();
        long worldDx = Math.round(deltaX * span / (double) Math.max(1, viewportWidth));
        long worldDy = Math.round(deltaY * span / (double) Math.max(1, viewportHeight));
        centerOn(centerX - worldDx, centerY + worldDy);
    }

    /** Zooms by choosing a nested sampling level; it never changes Terrain truth. */
    public boolean zoom(boolean closer) {
        int nextStep;
        if (closer) {
            nextStep = Math.max(MIN_SAMPLE_STEP, sampleStep / 2);
        } else {
            nextStep = Math.min(MAX_SAMPLE_STEP, sampleStep * 2);
        }
        if (nextStep == sampleStep) return false;
        sampleStep = nextStep;
        centerX = alignedClampedCenter(centerX, model.worldWidth());
        centerY = alignedClampedCenter(centerY, model.worldHeight());
        dirty = true;
        return true;
    }

    public void resetToMapCenter() {
        sampleStep = DEFAULT_SAMPLE_STEP;
        yawDegrees = 42.0f;
        pitchDegrees = 38.0f;
        centerOn(Math.round(model.centerX()), Math.round(model.centerY()));
    }

    public void invalidateSurface() {
        dirty = true;
    }

    public void render(int viewportWidth, int viewportHeight) {
        refreshSurfaceIfNeeded();
        updateCamera(viewportWidth, viewportHeight);

        renderer.begin(camera.combined, GL20.GL_TRIANGLES);
        for (int y = 0; y < SAMPLE_SIDE - 1; y++) {
            for (int x = 0; x < SAMPLE_SIDE - 1; x++) {
                emitVertex(x, y);
                emitVertex(x + 1, y);
                emitVertex(x, y + 1);

                emitVertex(x + 1, y);
                emitVertex(x + 1, y + 1);
                emitVertex(x, y + 1);
            }
        }
        renderer.end();
    }

    @Override
    public void close() {
        renderer.dispose();
    }

    private void refreshSurfaceIfNeeded() {
        if (!dirty && sampledSourceRevision == model.sourceRevision()) return;

        long minX = centerX - (long) HALF_SAMPLES * sampleStep;
        long minY = centerY - (long) HALF_SAMPLES * sampleStep;
        ContinuumScalarPage page = model.materializeSurface(new ContinuumSampleWindow(
                minX,
                minY,
                SAMPLE_SIDE,
                SAMPLE_SIDE,
                sampleStep));

        float horizontalScale = LOCAL_WIDTH / sampledWorldSpan();
        for (int y = 0; y < SAMPLE_SIDE; y++) {
            for (int x = 0; x < SAMPLE_SIDE; x++) {
                int index = index(x, y);
                surfaceZ[index] = (float) page.sample(x, y);
                localHeight[index] = surfaceZ[index] * horizontalScale * VERTICAL_EXAGGERATION;
            }
        }
        computeLighting();
        sampledSourceRevision = model.sourceRevision();
        dirty = false;
    }

    private void computeLighting() {
        float localStep = LOCAL_WIDTH / (SAMPLE_SIDE - 1);
        for (int y = 0; y < SAMPLE_SIDE; y++) {
            int y0 = Math.max(0, y - 1);
            int y1 = Math.min(SAMPLE_SIDE - 1, y + 1);
            for (int x = 0; x < SAMPLE_SIDE; x++) {
                int x0 = Math.max(0, x - 1);
                int x1 = Math.min(SAMPLE_SIDE - 1, x + 1);
                float dxSpan = Math.max(localStep, (x1 - x0) * localStep);
                float dySpan = Math.max(localStep, (y1 - y0) * localStep);
                float dzdx = (localHeight[index(x1, y)] - localHeight[index(x0, y)]) / dxSpan;
                float dzdy = (localHeight[index(x, y1)] - localHeight[index(x, y0)]) / dySpan;

                float nx = -dzdx;
                float ny = 1.0f;
                float nz = -dzdy;
                float inverseLength = 1.0f / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                nx *= inverseLength;
                ny *= inverseLength;
                nz *= inverseLength;

                float diffuse = Math.max(0.0f, nx * -0.35f + ny * 0.86f + nz * 0.37f);
                shade[index(x, y)] = 0.62f + 0.38f * diffuse;
            }
        }
    }

    private void emitVertex(int x, int y) {
        int index = index(x, y);
        float unit = LOCAL_WIDTH / (SAMPLE_SIDE - 1);
        float localX = -LOCAL_WIDTH * 0.5f + x * unit;
        float localZ = -LOCAL_WIDTH * 0.5f + y * unit;
        setTerrainColor(surfaceZ[index], shade[index]);
        renderer.vertex(localX, localHeight[index], localZ);
    }

    private void setTerrainColor(float z, float lighting) {
        float r;
        float g;
        float b;
        if (z < 0.0f) {
            float depth = clamp(-z / 2_200.0f, 0.0f, 1.0f);
            r = lerp(0.10f, 0.025f, depth);
            g = lerp(0.34f, 0.08f, depth);
            b = lerp(0.55f, 0.24f, depth);
        } else {
            float height = clamp(z / 1_800.0f, 0.0f, 1.0f);
            if (height < 0.55f) {
                float amount = height / 0.55f;
                r = lerp(0.18f, 0.48f, amount);
                g = lerp(0.45f, 0.42f, amount);
                b = lerp(0.20f, 0.26f, amount);
            } else {
                float amount = (height - 0.55f) / 0.45f;
                r = lerp(0.48f, 0.82f, amount);
                g = lerp(0.42f, 0.80f, amount);
                b = lerp(0.26f, 0.76f, amount);
            }
        }
        renderer.color(r * lighting, g * lighting, b * lighting, 1.0f);
    }

    private void updateCamera(int width, int height) {
        camera.viewportWidth = Math.max(1, width);
        camera.viewportHeight = Math.max(1, height);

        double yaw = Math.toRadians(yawDegrees);
        double pitch = Math.toRadians(pitchDegrees);
        float horizontalDistance = (float) (Math.cos(pitch) * CAMERA_DISTANCE);
        camera.position.set(
                (float) (Math.sin(yaw) * horizontalDistance),
                (float) (Math.sin(pitch) * CAMERA_DISTANCE),
                (float) (Math.cos(yaw) * horizontalDistance));
        camera.up.set(0.0f, 1.0f, 0.0f);
        camera.lookAt(0.0f, 0.0f, 0.0f);
        camera.update();
    }

    private long alignedClampedCenter(long requested, long worldExtent) {
        long halfSpan = (long) HALF_SAMPLES * sampleStep;
        long minimum = halfSpan;
        long maximum = worldExtent - 1L - halfSpan;
        if (maximum < minimum) throw new IllegalStateException("3D inspection window exceeds world domain");
        long clamped = Math.max(minimum, Math.min(maximum, requested));
        long aligned = Math.round(clamped / (double) sampleStep) * (long) sampleStep;
        return Math.max(minimum, Math.min(maximum, aligned));
    }

    private static int index(int x, int y) {
        return y * SAMPLE_SIDE + x;
    }

    private static float wrapDegrees(float degrees) {
        float wrapped = degrees % 360.0f;
        return wrapped < 0.0f ? wrapped + 360.0f : wrapped;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
