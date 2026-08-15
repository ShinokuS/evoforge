package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;

/**
 * Fixed-budget screen-space rain. There are no raindrop entities or per-frame
 * allocations; streak positions are reconstructed from immutable seeds + time.
 */
public final class RainRenderer {

    static final int MAX_STREAKS = 160;
    private static final int MIN_STREAKS = 44;
    private static final int FAR_STREAKS = 48;
    private static final float OFFSCREEN_MARGIN = 40f;

    private WeatherPresentationLookup weather;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Matrix4 projection = new Matrix4();
    private final float[] seedX = new float[MAX_STREAKS];
    private final float[] seedY = new float[MAX_STREAKS];
    private int width = 1;
    private int height = 1;

    public RainRenderer(
            WeatherPresentationLookup weather) {

        setWeather(weather);
        initializeSeeds();
    }

    public void setWeather(
            WeatherPresentationLookup weather) {

        if (weather == null) {
            throw new IllegalArgumentException(
                    "weather must not be null");
        }
        this.weather = weather;
    }

    public void resize(
            int width,
            int height) {

        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw(
            float presentationSeconds) {

        WeatherPresentation current = weather.current();
        if (current == null) {
            throw new IllegalStateException(
                    "weather lookup returned null");
        }
        if (current.kind() != WeatherPresentationKind.RAIN
                || current.intensity() <= 0f) {
            return;
        }

        float intensity = clamp01(current.intensity());
        int active = activeStreakCount(intensity);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(
                GL20.GL_SRC_ALPHA,
                GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(projection);

        // A restrained veil makes the weather state immediately legible while the
        // streaks still carry the visible falling direction.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(
                0.07f,
                0.12f,
                0.16f,
                0.050f + 0.075f * intensity);
        shapes.rect(0f, 0f, width, height);
        shapes.end();

        Gdx.gl.glLineWidth(1f);
        shapes.begin(ShapeRenderer.ShapeType.Line);

        int farCount = Math.min(FAR_STREAKS, active);
        shapes.setColor(
                0.62f,
                0.80f,
                0.90f,
                0.30f + 0.16f * intensity);
        for (int index = 0; index < farCount; index++) {
            drawStreak(index, presentationSeconds, 270f, 12f, 4f);
        }

        shapes.setColor(
                0.82f,
                0.93f,
                0.98f,
                0.46f + 0.26f * intensity);
        for (int index = farCount; index < active; index++) {
            drawStreak(index, presentationSeconds, 470f, 21f, 7f);
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    static int activeStreakCount(float intensity) {
        float clamped = clamp01(intensity);
        return Math.min(
                MAX_STREAKS,
                MIN_STREAKS + Math.round((MAX_STREAKS - MIN_STREAKS) * clamped));
    }

    private void drawStreak(
            int index,
            float seconds,
            float speed,
            float length,
            float slant) {

        float spanX = width + OFFSCREEN_MARGIN * 2f;
        float spanY = height + OFFSCREEN_MARGIN * 2f;
        float travel = seconds * speed;
        float y = positiveModulo(
                seedY[index] * spanY - travel,
                spanY) - OFFSCREEN_MARGIN;
        float x = positiveModulo(
                seedX[index] * spanX + travel * 0.18f,
                spanX) - OFFSCREEN_MARGIN;
        float variance = 0.78f + seedX[index] * 0.38f;
        shapes.line(
                x,
                y,
                x + slant * variance,
                y + length * variance);
    }

    private void initializeSeeds() {
        int state = 0x51A7C3D;
        for (int index = 0; index < MAX_STREAKS; index++) {
            state = state * 1664525 + 1013904223;
            seedX[index] = unitFloat(state);
            state = state * 1664525 + 1013904223;
            seedY[index] = unitFloat(state);
        }
    }

    private static float unitFloat(
            int value) {

        return (value >>> 8 & 0x00FFFFFF)
                / (float) 0x01000000;
    }

    private static float positiveModulo(
            float value,
            float modulus) {

        float result = value % modulus;
        return result < 0f ? result + modulus : result;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
