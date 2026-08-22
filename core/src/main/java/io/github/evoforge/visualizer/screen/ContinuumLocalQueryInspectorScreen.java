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
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.evoforge.simulation.world.continuum.field.ContinuumSampleWindow;
import io.github.evoforge.visualizer.continuum.ContinuumLocalQueryInspectorModel;

/** Plain visual proof for Stage 1 shared local-query work. */
public final class ContinuumLocalQueryInspectorScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.035f, 0.045f, 0.052f, 1f);
    private static final Color SHARED_FILL = new Color(0.12f, 0.36f, 0.24f, 0.50f);
    private static final Color SHARED_LINE = new Color(0.30f, 0.92f, 0.58f, 1f);
    private static final Color REQUEST_LINE = new Color(0.30f, 0.70f, 1.00f, 0.72f);
    private static final Color LOCAL_RESULT = new Color(1.00f, 0.86f, 0.30f, 1f);
    private static final Color MUTED = new Color(0.70f, 0.74f, 0.76f, 1f);

    private final Runnable returnToMenu;
    private final ContinuumLocalQueryInspectorModel model = new ContinuumLocalQueryInspectorModel();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new InspectorInput();

    private int width = 1;
    private int height = 1;
    private float pixelsPerWorldUnit = 3.2f;

    public ContinuumLocalQueryInspectorScreen(Runnable returnToMenu) {
        if (returnToMenu == null) throw new IllegalArgumentException("returnToMenu must not be null");
        this.returnToMenu = returnToMenu;
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
        drawProof();
        drawExplanation();
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
        shapes.dispose();
        batch.dispose();
        skin.dispose();
    }

    private void drawProof() {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(SHARED_FILL);
        for (var key : model.sharedRegions()) {
            drawFilled(model.regionWindow(key));
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(SHARED_LINE);
        for (var key : model.sharedRegions()) {
            drawOutline(model.regionWindow(key), 0f);
        }

        int index = 0;
        for (var request : model.requests()) {
            shapes.setColor(index == 0 ? LOCAL_RESULT : REQUEST_LINE);
            drawOutline(request.window(), index == 0 ? 3f : 1.5f);
            index++;
        }
        shapes.end();
    }

    private void drawExplanation() {
        var metrics = model.batch().metrics();
        double savedPercent = metrics.totalRegionUses() == 0
                ? 0d
                : metrics.reusedRegionUses() * 100d / metrics.totalRegionUses();

        batch.setProjectionMatrix(projection);
        batch.begin();
        float x = 28f;
        float y = height - 28f;

        font.getData().setScale(1.0f);
        font.setColor(Color.WHITE);
        font.draw(batch, "STAGE 1 / SHARED LOCAL REQUESTS", x, y);

        font.getData().setScale(0.80f);
        y -= 32f;
        font.setColor(REQUEST_LINE);
        font.draw(batch, "BLUE  = area a consumer asks for", x, y);
        y -= 22f;
        font.setColor(SHARED_LINE);
        font.draw(batch, "GREEN = technical region calculated once and shared", x, y);
        y -= 22f;
        font.setColor(LOCAL_RESULT);
        font.draw(batch, "YELLOW = one consumer's returned local area (nothing outside it)", x, y);

        y -= 38f;
        font.getData().setScale(0.92f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Consumers asking now: " + metrics.consumerRequests(), x, y);
        y -= 26f;
        font.draw(batch, "Region uses without sharing: " + metrics.totalRegionUses(), x, y);
        y -= 26f;
        font.draw(batch, "Unique regions actually calculated: " + metrics.uniqueRegions(), x, y);
        y -= 26f;
        font.draw(batch,
                "Repeated region calculations avoided: " + metrics.reusedRegionUses()
                        + String.format("  (%.0f%%)", savedPercent),
                x,
                y);
        y -= 26f;
        font.draw(batch, "Actual new page loads: " + metrics.pageLoads(), x, y);
        y -= 26f;
        font.draw(batch, "Each consumer receives only: 32 x 32 samples", x, y);

        y -= 38f;
        font.getData().setScale(0.76f);
        font.setColor(MUTED);
        font.draw(batch,
                "Logical world: 1,000,000 x 1,000,000   |   world revision: " + model.revision(),
                x,
                y);
        y -= 22f;
        font.draw(batch,
                "The world size does not change this local work. Shared green regions are an optimization, not world truth.",
                x,
                y);

        font.setColor(Color.WHITE);
        font.draw(batch,
                "1 = one consumer   2 = ten   3 = one hundred   |   arrows move the example   R = new revision   Home = center   Esc = back",
                x,
                28f);
        batch.end();
    }

    private void drawFilled(ContinuumSampleWindow window) {
        float x = screenX(window.minX());
        float y = screenY(window.minY());
        float w = worldWidth(window) * pixelsPerWorldUnit;
        float h = worldHeight(window) * pixelsPerWorldUnit;
        shapes.rect(x, y, w, h);
    }

    private void drawOutline(ContinuumSampleWindow window, float insetPixels) {
        float x = screenX(window.minX()) + insetPixels;
        float y = screenY(window.minY()) + insetPixels;
        float w = worldWidth(window) * pixelsPerWorldUnit - insetPixels * 2f;
        float h = worldHeight(window) * pixelsPerWorldUnit - insetPixels * 2f;
        if (w > 0f && h > 0f) shapes.rect(x, y, w, h);
    }

    private float screenX(long worldX) {
        return width * 0.66f + (worldX - model.boundaryX()) * pixelsPerWorldUnit;
    }

    private float screenY(long worldY) {
        return height * 0.48f + (worldY - model.boundaryY()) * pixelsPerWorldUnit;
    }

    private static long worldWidth(ContinuumSampleWindow window) {
        return Math.multiplyExact((long) window.width(), window.step());
    }

    private static long worldHeight(ContinuumSampleWindow window) {
        return Math.multiplyExact((long) window.height(), window.step());
    }

    private void zoom(float factor) {
        pixelsPerWorldUnit = Math.max(1.6f, Math.min(6.0f, pixelsPerWorldUnit * factor));
    }

    private final class InspectorInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> model.setConsumerCount(1);
                case Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> model.setConsumerCount(10);
                case Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> model.setConsumerCount(100);
                case Input.Keys.LEFT -> model.moveByPages(-1L, 0L);
                case Input.Keys.RIGHT -> model.moveByPages(1L, 0L);
                case Input.Keys.DOWN -> model.moveByPages(0L, -1L);
                case Input.Keys.UP -> model.moveByPages(0L, 1L);
                case Input.Keys.R -> model.advanceRevision();
                case Input.Keys.HOME -> model.resetCenter();
                case Input.Keys.EQUALS -> zoom(1.15f);
                case Input.Keys.MINUS -> zoom(1f / 1.15f);
                case Input.Keys.ESCAPE -> returnToMenu.run();
                default -> { return false; }
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (amountY == 0f) return false;
            zoom(amountY < 0f ? 1.10f : 1f / 1.10f);
            return true;
        }
    }
}
