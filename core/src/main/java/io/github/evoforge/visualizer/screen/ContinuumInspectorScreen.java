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
import io.github.evoforge.simulation.world.continuum.page.ContinuumPageKey;
import io.github.evoforge.visualizer.continuum.ContinuumInspectorModel;
import java.util.HashSet;
import java.util.Set;

/** Developer-only Continuum page/cache and Stage 3 multi-resolution inspection surface. */
public final class ContinuumInspectorScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.035f, 0.045f, 0.052f, 1f);
    private static final Color GRID = new Color(0.18f, 0.22f, 0.24f, 1f);
    private static final Color RESIDENT_FILL = new Color(0.07f, 0.12f, 0.14f, 1f);
    private static final Color RESIDENT_LINE = new Color(0.34f, 0.78f, 0.72f, 1f);
    private static final Color REQUEST_LINE = new Color(0.78f, 0.94f, 0.96f, 1f);
    private static final Color EVICTED_LINE = new Color(0.95f, 0.48f, 0.38f, 1f);
    private static final Color FOCUS_LINE = new Color(1f, 0.88f, 0.36f, 1f);
    private static final Color MUTED = new Color(0.68f, 0.72f, 0.74f, 1f);

    private static final float MIN_PAGE_PIXELS = 18f;
    private static final float MAX_PAGE_PIXELS = 112f;

    private final Runnable returnToMenu;
    private final ContinuumInspectorModel model;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new InspectorInput();

    private int width = 1;
    private int height = 1;
    private float pagePixels = 56f;

    public ContinuumInspectorScreen(Runnable returnToMenu) {
        if (returnToMenu == null) throw new IllegalArgumentException("returnToMenu must not be null");
        this.returnToMenu = returnToMenu;
        this.model = ContinuumInspectorModel.standard();
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
        drawPageField();
        drawOverlayText();
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

    private void drawPageField() {
        ContinuumPageKey focus = model.focus();
        Set<ContinuumPageKey> requested = new HashSet<>(model.requestedKeys());
        Set<ContinuumPageKey> resident = new HashSet<>(model.residentKeys());
        Set<ContinuumPageKey> evicted = new HashSet<>(model.lastEvictedKeys());

        int radiusX = Math.max(2, (int) Math.ceil(width / (2f * pagePixels)) + 2);
        int radiusY = Math.max(2, (int) Math.ceil(height / (2f * pagePixels)) + 2);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (long pageY = Math.max(0L, focus.pageY() - radiusY);
                pageY <= Math.min(model.pageCountY() - 1L, focus.pageY() + radiusY);
                pageY++) {
            for (long pageX = Math.max(0L, focus.pageX() - radiusX);
                    pageX <= Math.min(model.pageCountX() - 1L, focus.pageX() + radiusX);
                    pageX++) {
                ContinuumPageKey key = new ContinuumPageKey(pageX, pageY);
                float x = screenX(pageX, focus.pageX());
                float y = screenY(pageY, focus.pageY());

                if (requested.contains(key)) {
                    float value = (float) model.requestedValue(key).orElse(0.5d);
                    shapes.setColor(
                            0.10f + value * 0.10f,
                            0.20f + value * 0.26f,
                            0.26f + value * 0.30f,
                            1f);
                    shapes.rect(x + 1f, y + 1f, pagePixels - 2f, pagePixels - 2f);
                } else if (resident.contains(key)) {
                    shapes.setColor(RESIDENT_FILL);
                    shapes.rect(x + 1f, y + 1f, pagePixels - 2f, pagePixels - 2f);
                }
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (long pageY = Math.max(0L, focus.pageY() - radiusY);
                pageY <= Math.min(model.pageCountY() - 1L, focus.pageY() + radiusY);
                pageY++) {
            for (long pageX = Math.max(0L, focus.pageX() - radiusX);
                    pageX <= Math.min(model.pageCountX() - 1L, focus.pageX() + radiusX);
                    pageX++) {
                ContinuumPageKey key = new ContinuumPageKey(pageX, pageY);
                float x = screenX(pageX, focus.pageX());
                float y = screenY(pageY, focus.pageY());

                shapes.setColor(GRID);
                shapes.rect(x, y, pagePixels, pagePixels);
                if (resident.contains(key)) {
                    shapes.setColor(RESIDENT_LINE);
                    shapes.rect(x + 2f, y + 2f, pagePixels - 4f, pagePixels - 4f);
                }
                if (requested.contains(key)) {
                    shapes.setColor(REQUEST_LINE);
                    shapes.rect(x + 4f, y + 4f, pagePixels - 8f, pagePixels - 8f);
                }
                if (evicted.contains(key)) {
                    shapes.setColor(EVICTED_LINE);
                    shapes.rect(x + 6f, y + 6f, pagePixels - 12f, pagePixels - 12f);
                }
                if (key.equals(focus)) {
                    shapes.setColor(FOCUS_LINE);
                    shapes.rect(x + 8f, y + 8f, pagePixels - 16f, pagePixels - 16f);
                }
            }
        }
        shapes.end();
    }

    private void drawOverlayText() {
        var metrics = model.metrics();
        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(0.9f);
        font.setColor(Color.WHITE);
        float x = 28f;
        float y = height - 28f;
        font.draw(batch, "CONTINUUM / STAGE 3 MULTI-RESOLUTION INSPECTOR", x, y);

        font.getData().setScale(0.76f);
        font.setColor(MUTED);
        y -= 30f;
        font.draw(batch,
                "diagnostic scalar only — nested sampling grids read the SAME coordinate-addressed field",
                x,
                y);
        y -= 26f;
        font.draw(batch,
                "logical=" + model.logicalWidth() + "x" + model.logicalHeight()
                        + "  resolution=L" + model.resolutionLevel()
                        + "  step=" + model.sampleStep() + " world-units/sample"
                        + "  pageSpan=" + model.pageWorldSpanX() + "x" + model.pageWorldSpanY(),
                x,
                y);
        y -= 22f;
        font.draw(batch,
                "pageSamples=" + model.pageSide() + "x" + model.pageSide()
                        + "  pages@L" + model.resolutionLevel() + "=" + model.pageCountX() + "x" + model.pageCountY()
                        + "  maxLevel=" + model.maxResolutionLevel(),
                x,
                y);
        y -= 22f;
        font.draw(batch,
                "focusPage=" + model.focus().pageX() + "," + model.focus().pageY()
                        + "  focusWorld=" + model.focusWorldX() + "," + model.focusWorldY()
                        + "  seed=" + Long.toUnsignedString(model.seed()),
                x,
                y);
        y -= 22f;
        font.draw(batch,
                "resident=" + metrics.residentPages() + "/" + metrics.maxResidentPages()
                        + "  payload=" + formatMiB(metrics.residentPayloadBytes())
                        + "/" + formatMiB(metrics.maxResidentPayloadBytes())
                        + "  requested=" + model.requestedKeys().size()
                        + "  lastEvicted=" + model.lastEvictedKeys().size(),
                x,
                y);
        y -= 22f;
        font.draw(batch,
                "hits=" + metrics.hits()
                        + "  misses=" + metrics.misses()
                        + "  loads=" + metrics.loads()
                        + "  evictions=" + metrics.evictions()
                        + "  presentationZoom=" + Math.round(pagePixels) + " px/page",
                x,
                y);

        font.setColor(REQUEST_LINE);
        font.draw(batch,
                "Arrows/WASD move | Shift+move 8 pages | PageDown coarser | PageUp finer | +/-/wheel visual zoom | Home center | Esc back",
                x,
                28f);
        batch.end();
    }

    private float screenX(long pageX, long focusPageX) {
        return width * 0.5f + (pageX - focusPageX) * pagePixels - pagePixels * 0.5f;
    }

    private float screenY(long pageY, long focusPageY) {
        return height * 0.5f + (pageY - focusPageY) * pagePixels - pagePixels * 0.5f;
    }

    private void zoom(float factor) {
        pagePixels = Math.max(MIN_PAGE_PIXELS, Math.min(MAX_PAGE_PIXELS, pagePixels * factor));
    }

    private static String formatMiB(long bytes) {
        return String.format("%.2f MiB", bytes / (1024d * 1024d));
    }

    private final class InspectorInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            long step = (Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT)) ? 8L : 1L;
            switch (keycode) {
                case Input.Keys.LEFT, Input.Keys.A -> model.moveFocus(-step, 0L);
                case Input.Keys.RIGHT, Input.Keys.D -> model.moveFocus(step, 0L);
                case Input.Keys.DOWN, Input.Keys.S -> model.moveFocus(0L, -step);
                case Input.Keys.UP, Input.Keys.W -> model.moveFocus(0L, step);
                case Input.Keys.PAGE_DOWN -> model.coarsenResolution();
                case Input.Keys.PAGE_UP -> model.refineResolution();
                case Input.Keys.HOME -> model.resetCenter();
                case Input.Keys.EQUALS -> zoom(1.18f);
                case Input.Keys.MINUS -> zoom(1f / 1.18f);
                case Input.Keys.ESCAPE -> returnToMenu.run();
                default -> {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (amountY == 0f) return false;
            zoom(amountY < 0f ? 1.12f : 1f / 1.12f);
            return true;
        }
    }
}
