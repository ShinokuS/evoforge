package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.visualizer.ZLevelVisualizer;
import io.github.evoforge.visualizer.scenario.ScenarioSession;
import io.github.evoforge.visualizer.scenario.ScenarioView;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;

/** Hosts one fresh scenario runtime and the shared generic visualizer. */
public final class ScenarioScreen extends ScreenAdapter {

    private final VisualizerScenario scenario;
    private final Runnable restart;
    private final Runnable backToScenarios;
    private final ZLevelVisualizer visualizer;
    private final InputMultiplexer input;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    private int width = 1;
    private int height = 1;

    public ScenarioScreen(
            VisualizerScenario scenario,
            Runnable restart,
            Runnable backToScenarios) {

        if (scenario == null) {
            throw new IllegalArgumentException("scenario must not be null");
        }
        if (restart == null) {
            throw new IllegalArgumentException("restart must not be null");
        }
        if (backToScenarios == null) {
            throw new IllegalArgumentException(
                    "backToScenarios must not be null");
        }

        this.scenario = scenario;
        this.restart = restart;
        this.backToScenarios = backToScenarios;

        ScenarioSession session = scenario.create();
        SimulationRuntime runtime = session.runtime();
        visualizer = new ZLevelVisualizer(
                runtime.view(),
                runtime.time(),
                runtime.stepper());

        ScenarioView initial = session.view();
        visualizer.setView(
                initial.selectedZ(),
                initial.cameraX(),
                initial.cameraY(),
                initial.zoom());

        input = new InputMultiplexer(
                new SessionInput(),
                visualizer.inputProcessor());
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        visualizer.render();
        drawScenarioLabel();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
        visualizer.resize(width, height);
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == input) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        hide();
        visualizer.dispose();
        batch.dispose();
        font.dispose();
    }

    private void drawScenarioLabel() {
        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(0.9f);

        String title = "SCENARIO  " + scenario.title();
        String detail = scenario.description()
                + "   |   R restart   |   Esc scenarios";

        drawShadowed(title, 14f, 48f, Color.WHITE);
        drawShadowed(detail, 14f, 24f, Color.LIGHT_GRAY);
        batch.end();
    }

    private void drawShadowed(
            String text,
            float x,
            float y,
            Color color) {

        font.setColor(Color.BLACK);
        font.draw(batch, text, x + 1f, y - 1f);
        font.setColor(color);
        font.draw(batch, text, x, y);
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
