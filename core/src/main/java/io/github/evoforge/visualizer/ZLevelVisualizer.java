package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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
        Gdx.input.setInputProcessor(input);
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        input.update(delta);
        time.update(delta);
        camera.update();
        updateLandscapeSampling();

        Gdx.gl.glClearColor(
                BACKGROUND.r,
                BACKGROUND.g,
                BACKGROUND.b,
                BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        VisualizerCamera.VisibleRange range = camera.visibleRange();

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

        overlayRenderer.draw(range);
        hudRenderer.draw();
    }

    public void resize(
            int width,
            int height) {

        camera.resize(width, height);
        hudRenderer.resize(width, height);
    }

    public void dispose() {
        if (Gdx.input.getInputProcessor() == input) {
            Gdx.input.setInputProcessor(null);
        }
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
