package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import io.github.evoforge.simulation.control.movement.CancelMoveToCommand;
import io.github.evoforge.simulation.control.movement.CancelMoveToResult;
import io.github.evoforge.simulation.control.movement.MoveToCommand;
import io.github.evoforge.simulation.control.movement.MoveToResult;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerCommandSink;
import io.github.evoforge.visualizer.ZLevelVisualizer;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentation;
import io.github.evoforge.visualizer.presentation.weather.WeatherPresentationKind;
import io.github.evoforge.visualizer.scenario.ScenarioDiagnostics;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Hosts one fresh scenario runtime and the shared generic visualizer. */
public final class ScenarioScreen extends ScreenAdapter {
    private static final float LABEL_MARGIN = 14f;
    private static final float LABEL_GAP = 5f;

    private final VisualizerScenario scenario;
    private final Runnable restart;
    private final Runnable backToScenarios;
    private final ScenarioSession session;
    private final ZLevelVisualizer visualizer;
    private final ScenarioDiagnosticRenderer diagnosticRenderer = new ScenarioDiagnosticRenderer();
    private final InputMultiplexer input;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Matrix4 screenProjection = new Matrix4();
    private final Matrix4 worldProjection = new Matrix4();
    private int screenWidth = 1;

    public ScenarioScreen(
            VisualizerScenario scenario,
            Runnable restart,
            Runnable backToScenarios) {

        if (scenario == null) throw new IllegalArgumentException("scenario must not be null");
        if (restart == null) throw new IllegalArgumentException("restart must not be null");
        if (backToScenarios == null) {
            throw new IllegalArgumentException("backToScenarios must not be null");
        }

        this.scenario = scenario;
        this.restart = restart;
        this.backToScenarios = backToScenarios;

        font.setUseIntegerPositions(true);
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Nearest,
                Texture.TextureFilter.Nearest);

        session = scenario.create();
        SimulationRuntime runtime = session.runtime();
        visualizer = new ZLevelVisualizer(
                runtime.view(),
                runtime.time(),
                runtime.stepper(),
                session.objectPresentations());
        visualizer.setInteractionBindings(session.portals(), commandSink(runtime));
        visualizer.setWeatherPresentation(session.weather());

        ScenarioView initial = session.view();
        visualizer.setView(
                initial.selectedZ(),
                initial.cameraX(),
                initial.cameraY(),
                initial.zoom());

        input = new InputMultiplexer(
                new ScenarioCellInput(session, visualizer),
                visualizer.inputProcessor(),
                new SessionInput());
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override public void show() { Gdx.input.setInputProcessor(input); }

    @Override
    public void render(float delta) {
        visualizer.render();
        session.update();
        ScenarioDiagnostics diagnostics = session.diagnostics();
        visualizer.copyWorldProjection(worldProjection);
        diagnosticRenderer.draw(diagnostics, worldProjection, visualizer.selectedZ());
        drawScenarioLabel(diagnostics);
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        screenWidth = width;
        visualizer.resize(width, height);
        screenProjection.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == input) Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        hide();
        visualizer.dispose();
        diagnosticRenderer.dispose();
        batch.dispose();
        font.dispose();
    }

    private void drawScenarioLabel(ScenarioDiagnostics diagnostics) {
        batch.setProjectionMatrix(screenProjection);
        batch.begin();

        String title = "SCENARIO  " + scenario.title();
        String detail = scenario.description()
                + "   |   " + weatherLabel(session.weather().current())
                + "   |   R restart   |   Esc cancel/back";
        String summary = diagnostics.summary();
        float textWidth = Math.max(1f, screenWidth - LABEL_MARGIN * 2f);

        float titleHeight = measure(title, textWidth);
        float detailHeight = measure(detail, textWidth);
        float summaryHeight = summary.isEmpty() ? 0f : measure(summary, textWidth);
        float totalHeight = titleHeight + LABEL_GAP + detailHeight
                + (summary.isEmpty() ? 0f : LABEL_GAP + summaryHeight);
        float top = LABEL_MARGIN + totalHeight;

        drawShadowed(title, LABEL_MARGIN, top, textWidth, Color.WHITE);
        top -= titleHeight + LABEL_GAP;
        drawShadowed(detail, LABEL_MARGIN, top, textWidth, Color.LIGHT_GRAY);
        if (!summary.isEmpty()) {
            top -= detailHeight + LABEL_GAP;
            drawShadowed(summary, LABEL_MARGIN, top, textWidth, Color.LIGHT_GRAY);
        }
        batch.end();
    }

    private static VisualizerCommandSink commandSink(SimulationRuntime runtime) {
        return new VisualizerCommandSink() {
            @Override
            public CommandFeedback moveTo(ObjectId objectId, int x, int y, int z) {
                MoveToResult result = runtime.submit(new MoveToCommand(objectId, x, y, z));
                return result.accepted()
                        ? CommandFeedback.accepted("")
                        : CommandFeedback.rejected(result.code().value());
            }

            @Override
            public CommandFeedback cancelMove(ObjectId objectId) {
                CancelMoveToResult result = runtime.submit(new CancelMoveToCommand(objectId));
                return result.accepted()
                        ? CommandFeedback.accepted("")
                        : CommandFeedback.rejected(result.code().value());
            }
        };
    }

    private static String weatherLabel(WeatherPresentation weather) {
        if (weather == null) throw new IllegalStateException("weather lookup returned null");
        if (weather.kind() == WeatherPresentationKind.RAIN) {
            return "Weather: Rain " + Math.round(weather.intensity() * 100f) + "%";
        }
        return "Weather: Clear";
    }

    private float measure(String text, float targetWidth) {
        glyphLayout.setText(font, text, Color.WHITE, targetWidth, Align.left, true);
        return Math.max(font.getLineHeight(), glyphLayout.height);
    }

    private void drawShadowed(String text, float x, float y, float targetWidth, Color color) {
        font.setColor(Color.BLACK);
        font.draw(batch, text, Math.round(x + 1f), Math.round(y - 1f), targetWidth, Align.left, true);
        font.setColor(color);
        font.draw(batch, text, Math.round(x), Math.round(y), targetWidth, Align.left, true);
    }

    private final class SessionInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            if (keycode == Input.Keys.R) {
                restart.run();
                return true;
            }
            if (keycode == Input.Keys.ESCAPE) {
                backToScenarios.run();
                return true;
            }
            return false;
        }
    }
}
