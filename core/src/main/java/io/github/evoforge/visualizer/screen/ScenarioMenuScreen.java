package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.function.Consumer;

/** Minimal keyboard/mouse selector for deterministic visualizer scenarios. */
public final class ScenarioMenuScreen extends ScreenAdapter {

    private static final Color BACKGROUND =
            new Color(0.035f, 0.045f, 0.052f, 1f);
    private static final float ROW_SPACING = 36f;

    private final ScenarioCatalog catalog;
    private final Consumer<VisualizerScenario> openScenario;
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new MenuInput();

    private int selectedIndex;
    private int width = 1;
    private int height = 1;

    public ScenarioMenuScreen(
            ScenarioCatalog catalog,
            Consumer<VisualizerScenario> openScenario) {

        if (catalog == null) {
            throw new IllegalArgumentException("catalog must not be null");
        }
        if (openScenario == null) {
            throw new IllegalArgumentException("openScenario must not be null");
        }
        this.catalog = catalog;
        this.openScenario = openScenario;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(
                BACKGROUND.r,
                BACKGROUND.g,
                BACKGROUND.b,
                BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        batch.setProjectionMatrix(projection);
        batch.begin();

        font.getData().setScale(1.35f);
        font.setColor(Color.WHITE);
        font.draw(batch, "EVOFORGE DEBUG SCENARIOS", 48f, height - 54f);

        font.getData().setScale(0.95f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(
                batch,
                "Up/Down select | Enter open | click a scenario",
                48f,
                height - 86f);

        float startY = height - 142f;
        for (int i = 0; i < catalog.size(); i++) {
            VisualizerScenario scenario = catalog.get(i);
            font.setColor(i == selectedIndex ? Color.CYAN : Color.WHITE);
            font.draw(
                    batch,
                    (i == selectedIndex ? ">  " : "   ")
                            + (i + 1) + ". " + scenario.title(),
                    64f,
                    startY - i * ROW_SPACING);
        }

        VisualizerScenario selected = catalog.get(selectedIndex);
        float detailY = Math.max(
                70f,
                startY - catalog.size() * ROW_SPACING - 28f);
        font.setColor(Color.LIGHT_GRAY);
        font.draw(batch, selected.description(), 64f, detailY);
        font.setColor(Color.GRAY);
        font.draw(batch, "id: " + selected.id(), 64f, detailY - 26f);

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) {
            return;
        }
        this.width = width;
        this.height = height;
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
        batch.dispose();
        font.dispose();
    }

    private void select(int delta) {
        selectedIndex = Math.floorMod(selectedIndex + delta, catalog.size());
    }

    private void openSelected() {
        openScenario.accept(catalog.get(selectedIndex));
    }

    private final class MenuInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.UP -> {
                    select(-1);
                    return true;
                }
                case Input.Keys.DOWN -> {
                    select(1);
                    return true;
                }
                case Input.Keys.ENTER, Input.Keys.SPACE -> {
                    openSelected();
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public boolean touchDown(
                int screenX,
                int screenY,
                int pointer,
                int button) {

            if (button != Input.Buttons.LEFT) {
                return false;
            }

            float y = height - screenY;
            float startY = height - 142f;
            for (int i = 0; i < catalog.size(); i++) {
                float baseline = startY - i * ROW_SPACING;
                if (y >= baseline - 24f && y <= baseline + 10f) {
                    selectedIndex = i;
                    openSelected();
                    return true;
                }
            }
            return false;
        }
    }
}
