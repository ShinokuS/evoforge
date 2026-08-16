package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.visualizer.interaction.VisualizerDebugPanel;
import io.github.evoforge.visualizer.interaction.VisualizerDebugPanelController;
import io.github.evoforge.visualizer.interaction.VisualizerInteractionController;
import io.github.evoforge.visualizer.interaction.VisualizerPrimaryHudController;
import io.github.evoforge.visualizer.presentation.ProceduralShapePresentations;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.portal.ViewPortalLookup;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationLookup;
import io.github.evoforge.visualizer.render.LandscapeRenderer;
import io.github.evoforge.visualizer.render.MoveToRouteDiagnosticRenderer;
import io.github.evoforge.visualizer.render.ObjectPresentationRenderer;
import io.github.evoforge.visualizer.render.RainRenderer;
import io.github.evoforge.visualizer.render.SurfaceCliffRenderer;
import io.github.evoforge.visualizer.render.SurfaceLandscapeRenderer;
import io.github.evoforge.visualizer.render.VisionDiagnosticRenderer;
import io.github.evoforge.visualizer.render.VisualizerContextMenuRenderer;
import io.github.evoforge.visualizer.render.VisualizerDebugPanelRenderer;
import io.github.evoforge.visualizer.render.VisualizerOverlayRenderer;
import io.github.evoforge.visualizer.render.VisualizerPrimaryHudRenderer;
import io.github.evoforge.visualizer.render.VisualizerUiAssets;
import io.github.evoforge.visualizer.render.WaterRenderer;
import io.github.evoforge.visualizer.render.WorldGridRenderer;
import io.github.evoforge.visualizer.render.WorldInteractionOverlayRenderer;
import io.github.evoforge.visualizer.render.WorldViewHudRenderer;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;
import io.github.evoforge.visualizer.visual.ProceduralWaterArt;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Orchestrates presentation of the authoritative simulation view. */
public final class ZLevelVisualizer {
    private static final Color BACKGROUND = new Color(0.045f, 0.055f, 0.065f, 1f);
    private final VisualizerTimeController time;
    private final VisualizerState state = new VisualizerState();
    private final VisualizerCamera camera = new VisualizerCamera();
    private final VisualizerInputController input;
    private final VisualizerInteractionController interaction;
    private final VisualizerDebugPanel debugPanel = new VisualizerDebugPanel();
    private final VisualizerDebugPanelController debugPanelInput;
    private final InputProcessor inputProcessor;
    private final VisualizerPerformanceTelemetry performance = new VisualizerPerformanceTelemetry();
    private final SpriteBatch landscapeBatch = new SpriteBatch();
    private final ProceduralLandscapePack landscapePack = new ProceduralLandscapePack();
    private final ProceduralSliceArt sliceArt = new ProceduralSliceArt();
    private final ProceduralWaterArt waterArt = new ProceduralWaterArt();
    private final VisualizerUiAssets uiAssets = new VisualizerUiAssets();
    private final LandscapeSliceResolver sliceResolver;
    private final SurfaceProjectionResolver surfaceResolver;
    private final ShapePresentationRegistry shapePresentations;
    private final LandscapeRenderer landscapeRenderer;
    private final SurfaceLandscapeRenderer surfaceLandscapeRenderer;
    private final SurfaceCliffRenderer surfaceCliffs;
    private final WorldGridRenderer worldGrid;
    private final WaterRenderer waterRenderer;
    private final RainRenderer rainRenderer;
    private final VisionDiagnosticRenderer visionDiagnostics;
    private final MoveToRouteDiagnosticRenderer moveToRouteDiagnostics;
    private final VisualizerOverlayRenderer overlayRenderer;
    private final ObjectPresentationRenderer objectRenderer;
    private final WorldInteractionOverlayRenderer interactionOverlay;
    private final VisualizerContextMenuRenderer contextMenuRenderer;
    private final VisualizerDebugPanelRenderer debugPanelRenderer;
    private final VisualizerPrimaryHudRenderer primaryHud;
    private final WorldViewHudRenderer worldViewHud;
    private boolean smoothLandscapeSampling;
    private float presentationSeconds;

    public ZLevelVisualizer(
            SimulationView view,
            SimulationTime simulationTime,
            SimulationStepper stepper) {
        this(view, simulationTime, stepper, ObjectPresentationBindings.empty());
    }

    public ZLevelVisualizer(
            SimulationView view,
            SimulationTime simulationTime,
            SimulationStepper stepper,
            ObjectPresentationBindings objectPresentations) {
        if (view == null) throw new IllegalArgumentException("view must not be null");
        if (simulationTime == null) throw new IllegalArgumentException("simulationTime must not be null");
        if (stepper == null) throw new IllegalArgumentException("stepper must not be null");
        if (objectPresentations == null) {
            throw new IllegalArgumentException("objectPresentations must not be null");
        }

        time = new VisualizerTimeController(stepper, 0.25f);
        input = new VisualizerInputController(state, camera, time);
        sliceResolver = new LandscapeSliceResolver(view);
        surfaceResolver = new SurfaceProjectionResolver(view);
        interaction = new VisualizerInteractionController(
                view, state, camera, surfaceResolver, sliceResolver);
        debugPanelInput = new VisualizerDebugPanelController(state, debugPanel);

        shapePresentations = ProceduralShapePresentations.create(landscapePack, sliceArt);
        landscapeRenderer = new LandscapeRenderer(view, shapePresentations, sliceResolver);
        surfaceLandscapeRenderer = new SurfaceLandscapeRenderer(
                view, shapePresentations, surfaceResolver);
        surfaceCliffs = new SurfaceCliffRenderer(view, state, camera);
        worldGrid = new WorldGridRenderer(state, camera);
        waterRenderer = new WaterRenderer(view, waterArt);
        rainRenderer = new RainRenderer(WeatherPresentationLookup.CLEAR_LOOKUP);
        visionDiagnostics = new VisionDiagnosticRenderer(view, simulationTime, state, camera);
        moveToRouteDiagnostics = new MoveToRouteDiagnosticRenderer(view, state, camera);
        overlayRenderer = new VisualizerOverlayRenderer(
                view, state, camera, sliceResolver, shapePresentations);
        objectRenderer = new ObjectPresentationRenderer(
                view, simulationTime, state, camera, objectPresentations);
        interactionOverlay = new WorldInteractionOverlayRenderer(
                view, state, camera, surfaceResolver);
        contextMenuRenderer = new VisualizerContextMenuRenderer(interaction.menu(), uiAssets);
        debugPanelRenderer = new VisualizerDebugPanelRenderer(state, debugPanel, uiAssets);
        primaryHud = new VisualizerPrimaryHudRenderer(
                view,
                simulationTime,
                time,
                state,
                camera,
                objectPresentations,
                surfaceResolver,
                uiAssets);
        worldViewHud = new WorldViewHudRenderer(view, state, uiAssets);
        VisualizerPrimaryHudController primaryHudInput = new VisualizerPrimaryHudController(state, primaryHud);
        inputProcessor = new InputMultiplexer(debugPanelInput, primaryHudInput, interaction, input);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        updateLandscapeSampling();
    }

    public InputProcessor inputProcessor() { return inputProcessor; }
    public boolean cancelOrBack() { return interaction.cancelOrBack(); }

    /** Explicitly enables command-capable interaction after read-only renderer construction. */
    public void setInteractionBindings(
            ViewPortalLookup portals,
            VisualizerCommandSink commands) {
        if (portals == null || commands == null) {
            throw new IllegalArgumentException("interaction bindings must not be null");
        }
        interaction.configure(portals, commands);
        interactionOverlay.setPortals(portals);
    }

    /** Scenario presentation policy: autonomous acceptance scenes can remain inspection-only. */
    public void setManualMovementEnabled(boolean enabled) {
        interaction.setManualMovementEnabled(enabled);
    }

    public void setWeatherPresentation(WeatherPresentationLookup weather) {
        rainRenderer.setWeather(weather);
    }

    public void setView(int selectedZ, float cameraX, float cameraY, float zoom) {
        state.setSelectedZ(selectedZ);
        camera.setView(cameraX, cameraY, zoom);
        updateLandscapeSampling();
    }

    public void copyWorldProjection(Matrix4 target) {
        if (target == null) throw new IllegalArgumentException("target must not be null");
        target.set(camera.projection());
    }

    public int selectedZ() { return state.selectedZ(); }
    public VisualizerViewMode viewMode() { return state.viewMode(); }
    public VisualizerCamera.Cell cellAt(int screenX, int screenY) { return camera.cellAt(screenX, screenY); }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        presentationSeconds += Math.min(delta, 0.1f);
        long frameStart = System.nanoTime();
        input.update(delta);
        time.update(delta);
        camera.update();
        interaction.update();
        updateLandscapeSampling();
        long afterUpdate = System.nanoTime();

        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        VisualizerCamera.VisibleRange range = camera.visibleRange();

        long landscapeStart = System.nanoTime();
        landscapeBatch.setProjectionMatrix(camera.projection());
        landscapeBatch.begin();
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            surfaceLandscapeRenderer.draw(
                    landscapeBatch,
                    range.minX(), range.maxX(), range.minY(), range.maxY());
            waterRenderer.drawSurface(
                    landscapeBatch,
                    range.minX(), range.maxX(), range.minY(), range.maxY());
        } else {
            VisibleArea area = visibleArea(range);
            if (area != null) {
                landscapeRenderer.draw(
                        landscapeBatch,
                        area.minX(), area.maxX(), area.minY(), area.maxY(),
                        state.selectedZ(), state.lowerDepth());
                waterRenderer.draw(
                        landscapeBatch,
                        area.minX(), area.maxX(), area.minY(), area.maxY(),
                        state.selectedZ(), state.lowerDepth());
            }
        }
        landscapeBatch.end();
        if (state.viewMode() == VisualizerViewMode.SURFACE) surfaceCliffs.draw(range);
        worldGrid.draw(range);
        long afterLandscape = System.nanoTime();

        interactionOverlay.drawPortals(range);
        objectRenderer.draw(range);
        overlayRenderer.draw(range);
        moveToRouteDiagnostics.draw(range);
        if (state.showVisionDiagnostics()) visionDiagnostics.draw(range);
        interactionOverlay.drawFeedback();
        rainRenderer.draw(presentationSeconds);
        long afterOverlay = System.nanoTime();

        primaryHud.draw();
        debugPanel.setTopInset(primaryHud.rightPanelReservedHeight());
        worldViewHud.draw();
        debugPanelRenderer.draw();
        contextMenuRenderer.draw();
        long frameEnd = System.nanoTime();
        performance.record(
                delta,
                frameEnd - frameStart,
                afterUpdate - frameStart,
                afterLandscape - landscapeStart,
                afterOverlay - afterLandscape,
                frameEnd - afterOverlay);
    }

    public void resize(int width, int height) {
        camera.resize(width, height);
        interaction.resize(width, height);
        debugPanelInput.resize(width, height);
        rainRenderer.resize(width, height);
        primaryHud.resize(width, height);
        worldViewHud.resize(width, height);
        debugPanelRenderer.resize(width, height);
        contextMenuRenderer.resize(width, height);
    }

    public void dispose() {
        contextMenuRenderer.dispose();
        debugPanelRenderer.dispose();
        worldViewHud.dispose();
        primaryHud.dispose();
        interactionOverlay.dispose();
        objectRenderer.dispose();
        overlayRenderer.dispose();
        moveToRouteDiagnostics.dispose();
        visionDiagnostics.dispose();
        worldGrid.dispose();
        surfaceCliffs.dispose();
        surfaceLandscapeRenderer.dispose();
        rainRenderer.dispose();
        uiAssets.dispose();
        waterArt.dispose();
        sliceArt.dispose();
        landscapePack.dispose();
        landscapeBatch.dispose();
    }

    private VisibleArea visibleArea(VisualizerCamera.VisibleRange range) {
        if (state.viewMode() != VisualizerViewMode.INTERIOR || state.interior() == null) {
            return new VisibleArea(range.minX(), range.maxX(), range.minY(), range.maxY());
        }
        int minX = Math.max(range.minX(), state.interior().minX());
        int maxX = Math.min(range.maxX(), state.interior().maxX());
        int minY = Math.max(range.minY(), state.interior().minY());
        int maxY = Math.min(range.maxY(), state.interior().maxY());
        return minX > maxX || minY > maxY
                ? null
                : new VisibleArea(minX, maxX, minY, maxY);
    }

    private void updateLandscapeSampling() {
        boolean smooth = camera.smoothLandscapeSampling();
        if (smooth == smoothLandscapeSampling) return;
        smoothLandscapeSampling = smooth;
        Texture.TextureFilter filter = smooth
                ? Texture.TextureFilter.Linear
                : Texture.TextureFilter.Nearest;
        landscapePack.surface(0, 0).getTexture().setFilter(filter, filter);
        surfaceLandscapeRenderer.setFilter(filter);
        sliceArt.solid(0, 0).getTexture().setFilter(filter, filter);
        waterArt.setFilter(filter);
    }

    private record VisibleArea(int minX, int maxX, int minY, int maxY) { }
}
