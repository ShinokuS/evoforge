package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.visualizer.presentation.ProceduralShapePresentations;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.render.LandscapeRenderer;
import io.github.evoforge.visualizer.render.VisualizerHudRenderer;
import io.github.evoforge.visualizer.render.VisualizerOverlayRenderer;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/**
 * Orchestrates the debug presentation of the authoritative simulation view.
 *
 * <p>Camera, input, presentation state, world overlays and HUD each own their
 * existing responsibility. This class only coordinates their lifecycle and
 * render order; simulation mutation remains outside the presentation layer.</p>
 */
public final class ZLevelVisualizer {

    private static final Color BACKGROUND =
            new Color(0.045f, 0.055f, 0.065f, 1f);

    private final VisualizerTimeController time;
    private final VisualizerState state = new VisualizerState();
    private final VisualizerCamera camera = new VisualizerCamera();
    private final VisualizerInputController input;
    private final VisualizerPerformanceTelemetry performance =
            new VisualizerPerformanceTelemetry();

    private final SpriteBatch landscapeBatch = new SpriteBatch();
    private final ProceduralLandscapePack landscapePack =
            new ProceduralLandscapePack();
    private final ProceduralSliceArt sliceArt =
            new ProceduralSliceArt();
    private final LandscapeSliceResolver sliceResolver;
    private final ShapePresentationRegistry shapePresentations;
    private final LandscapeRenderer landscapeRenderer;
    private final VisualizerOverlayRenderer overlayRenderer;
    private final VisualizerHudRenderer hudRenderer;

    private boolean smoothLandscapeSampling;

    public ZLevelVisualizer(
            SimulationView view,
            SimulationTime simulationTime,
            SimulationStepper stepper) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (simulationTime == null) {
            throw new IllegalArgumentException(
                    "simulationTime must not be null");
        }
        if (stepper == null) {
            throw new IllegalArgumentException("stepper must not be null");
        }

        time = new VisualizerTimeController(stepper, 0.25f);
        input = new VisualizerInputController(view, state, camera, time);
        sliceResolver = new LandscapeSliceResolver(view);
        shapePresentations = ProceduralShapePresentations.create(
                landscapePack,
                sliceArt);
        landscapeRenderer = new LandscapeRenderer(
                view,
                shapePresentations,
                sliceResolver);
        overlayRenderer = new VisualizerOverlayRenderer(
                view,
                state,
                camera,
                sliceResolver,
                shapePresentations);
        hudRenderer = new VisualizerHudRenderer(
                view,
                simulationTime,
                time,
                state,
                camera,
                sliceResolver,
                shapePresentations);

        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateLandscapeSampling();
    }

    /** Physical input owned by this presentation session. */
    public InputProcessor inputProcessor() {
        return input;
    }

    /** Applies presentation-only initial focus without changing simulation state. */
    public void setView(
            int selectedZ,
            float cameraX,
            float cameraY,
            float zoom) {

        state.setSelectedZ(selectedZ);
        camera.setView(cameraX, cameraY, zoom);
        updateLandscapeSampling();
    }

    /** Copies the current world projection for an external presentation-only overlay. */
    public void copyWorldProjection(
            Matrix4 target) {

        if (target == null) {
            throw new IllegalArgumentException(
                    "target must not be null");
        }

        target.set(camera.projection());
    }

    /** Current standing Z selected by presentation controls. */
    public int selectedZ() {
        return state.selectedZ();
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        long frameStart = System.nanoTime();

        input.update(delta);
        time.update(delta);
        camera.update();
        updateLandscapeSampling();
        long afterUpdate = System.nanoTime();

        Gdx.gl.glClearColor(
                BACKGROUND.r,
                BACKGROUND.g,
                BACKGROUND.b,
                BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        VisualizerCamera.VisibleRange range = camera.visibleRange();

        long landscapeStart = System.nanoTime();
        landscapeBatch.setProjectionMatrix(camera.projection());
        landscapeBatch.begin();
        landscapeRenderer.draw(
                landscapeBatch,
                range.minX(),
                range.maxX(),
                range.minY(),
                range.maxY(),
                state.selectedZ(),
                state.lowerDepth());
        landscapeBatch.end();
        long afterLandscape = System.nanoTime();

        overlayRenderer.draw(range);
        long afterOverlay = System.nanoTime();

        hudRenderer.draw();
        long frameEnd = System.nanoTime();

        performance.record(
                delta,
                frameEnd - frameStart,
                afterUpdate - frameStart,
                afterLandscape - landscapeStart,
                afterOverlay - afterLandscape,
                frameEnd - afterOverlay);
    }

    public void resize(
            int width,
            int height) {

        camera.resize(width, height);
        hudRenderer.resize(width, height);
    }

    public void dispose() {
        hudRenderer.dispose();
        overlayRenderer.dispose();
        sliceArt.dispose();
        landscapePack.dispose();
        landscapeBatch.dispose();
    }

    private void updateLandscapeSampling() {
        boolean smooth = camera.smoothLandscapeSampling();
        if (smooth == smoothLandscapeSampling) {
            return;
        }

        smoothLandscapeSampling = smooth;
        Texture.TextureFilter filter = smooth
                ? Texture.TextureFilter.Linear
                : Texture.TextureFilter.Nearest;
        landscapePack.surface(0, 0).getTexture().setFilter(filter, filter);
        sliceArt.solid(0, 0).getTexture().setFilter(filter, filter);
    }
}
