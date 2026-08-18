package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;

/** Top-level navigation between simulation scenarios and world-generation tools. */
public final class VisualizerHomeScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.025f, 0.035f, 0.045f, 1f);
    private static final Color PANEL = new Color(0.065f, 0.078f, 0.088f, 1f);
    private static final Color SELECTED = new Color(0.12f, 0.28f, 0.32f, 1f);
    private static final Color ACCENT = new Color(0.38f, 0.90f, 0.94f, 1f);
    private static final Color MUTED = new Color(0.66f, 0.70f, 0.72f, 1f);

    private final Runnable openScenarios;
    private final Runnable openWorldGeneration;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new HomeInput();

    private int width = 1;
    private int height = 1;
    private int selected;

    public VisualizerHomeScreen(Runnable openScenarios, Runnable openWorldGeneration) {
        if (openScenarios == null || openWorldGeneration == null) {
            throw new IllegalArgumentException("workspace actions must not be null");
        }
        this.openScenarios = openScenarios;
        this.openWorldGeneration = openWorldGeneration;
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawPanels();
        drawText();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == input) Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        hide();
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }

    private void drawPanels() {
        float cardWidth = Math.min(430f, (width - 120f) / 2f);
        float cardHeight = 220f;
        float gap = 34f;
        float totalWidth = cardWidth * 2f + gap;
        float startX = (width - totalWidth) / 2f;
        float y = Math.max(90f, (height - cardHeight) / 2f - 10f);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int index = 0; index < 2; index++) {
            shapes.setColor(index == selected ? SELECTED : PANEL);
            shapes.rect(startX + index * (cardWidth + gap), y, cardWidth, cardHeight);
        }
        shapes.end();
    }

    private void drawText() {
        float cardWidth = Math.min(430f, (width - 120f) / 2f);
        float cardHeight = 220f;
        float gap = 34f;
        float totalWidth = cardWidth * 2f + gap;
        float startX = (width - totalWidth) / 2f;
        float y = Math.max(90f, (height - cardHeight) / 2f - 10f);

        batch.setProjectionMatrix(projection);
        batch.begin();

        font.getData().setScale(1.45f);
        font.setColor(Color.WHITE);
        font.draw(batch, "EVOFORGE DEVELOPMENT TOOLS", 52f, height - 52f);

        font.getData().setScale(0.94f);
        font.setColor(MUTED);
        font.draw(batch,
                "Choose a focused workspace instead of mixing simulation scenarios with world generation.",
                52f,
                height - 88f);

        drawCard(startX, y, "SCENARIOS",
                "Deterministic simulation acceptance scenarios, diagnostics and focused runtime experiments.",
                selected == 0);
        drawCard(startX + cardWidth + gap, y, "WORLD GENERATION",
                "Generate and inspect world-scale terrain with live intent and dimension controls.",
                selected == 1);

        font.getData().setScale(0.86f);
        font.setColor(MUTED);
        font.draw(batch, "Left/Right select | Enter open | mouse click open", 52f, 42f);
        batch.end();
    }

    private void drawCard(float x, float y, String title, String description, boolean active) {
        font.getData().setScale(1.12f);
        font.setColor(active ? ACCENT : Color.WHITE);
        font.draw(batch, title, x + 26f, y + 174f);

        font.getData().setScale(0.90f);
        font.setColor(active ? Color.WHITE : MUTED);
        font.draw(batch, description, x + 26f, y + 126f, 360f, Align.left, true);
    }

    private void activate() {
        if (selected == 0) openScenarios.run();
        else openWorldGeneration.run();
    }

    private int cardAt(int screenX, int screenY) {
        float cardWidth = Math.min(430f, (width - 120f) / 2f);
        float cardHeight = 220f;
        float gap = 34f;
        float totalWidth = cardWidth * 2f + gap;
        float startX = (width - totalWidth) / 2f;
        float y = Math.max(90f, (height - cardHeight) / 2f - 10f);
        float worldY = height - screenY;
        if (worldY < y || worldY > y + cardHeight) return -1;
        if (screenX >= startX && screenX <= startX + cardWidth) return 0;
        float secondX = startX + cardWidth + gap;
        if (screenX >= secondX && screenX <= secondX + cardWidth) return 1;
        return -1;
    }

    private final class HomeInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.LEFT, Input.Keys.UP -> selected = 0;
                case Input.Keys.RIGHT, Input.Keys.DOWN -> selected = 1;
                case Input.Keys.ENTER -> activate();
                default -> { return false; }
            }
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            int card = cardAt(screenX, screenY);
            if (card < 0) return false;
            selected = card;
            activate();
            return true;
        }
    }
}
