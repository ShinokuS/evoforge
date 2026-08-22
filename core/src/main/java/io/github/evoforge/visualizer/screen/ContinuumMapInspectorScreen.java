package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.visualizer.continuum.BoundedRenderCache;
import io.github.evoforge.visualizer.continuum.ContinuumMapInspectorModel;

/** Stage 4 world-oriented pan/zoom proof over a deterministic synthetic Continuum field. */
public final class ContinuumMapInspectorScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.025f, 0.032f, 0.038f, 1f);
    private static final Color FINE_BORDER = new Color(0.30f, 0.86f, 0.65f, 0.9f);
    private static final Color FALLBACK_BORDER = new Color(1.00f, 0.72f, 0.24f, 0.95f);
    private static final Color TEXT = new Color(0.94f, 0.96f, 0.97f, 1f);
    private static final Color MUTED = new Color(0.66f, 0.71f, 0.74f, 1f);
    private static final int MAX_GPU_TEXTURES = 192;

    private final Runnable returnToMenu;
    private final ContinuumMapInspectorModel model;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final BoundedRenderCache<ContinuumMapTile, Texture> textures =
            new BoundedRenderCache<>(MAX_GPU_TEXTURES, this::createTexture, Texture::dispose);
    private final InputAdapter input = new MapInput();

    private int width = 1;
    private int height = 1;
    private boolean showDiagnostics;
    private boolean dragging;
    private int lastDragX;
    private int lastDragY;

    public ContinuumMapInspectorScreen(Runnable returnToMenu) {
        if (returnToMenu == null) throw new IllegalArgumentException("returnToMenu must not be null");
        this.returnToMenu = returnToMenu;
        this.width = Math.max(1, Gdx.graphics.getWidth());
        this.height = Math.max(1, Gdx.graphics.getHeight());
        this.model = ContinuumMapInspectorModel.standard(width, height);
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        model.update(width, height);
        Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        drawMap();
        if (showDiagnostics) drawTileDiagnostics();
        drawOverlay();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
        model.update(width, height);
    }

    @Override
    public void hide() {
        if (Gdx.input.getInputProcessor() == input) Gdx.input.setInputProcessor(null);
    }

    @Override
    public void dispose() {
        hide();
        textures.close();
        model.close();
        shapes.dispose();
        batch.dispose();
        skin.dispose();
    }

    private void drawMap() {
        ContinuumMapViewport.Frame frame = model.frame();
        long span = model.tileWorldSpan(frame.desiredLevel());
        double scale = model.pixelsPerWorldUnit();

        batch.setProjectionMatrix(projection);
        batch.begin();
        for (ContinuumMapViewport.DisplayTile display : frame.tiles()) {
            long worldX = Math.multiplyExact(display.targetKey().tileX(), span);
            long worldY = Math.multiplyExact(display.targetKey().tileY(), span);
            float x = (float) model.screenXForWorld(worldX);
            float y = (float) model.screenYForWorld(worldY);
            float size = (float) (span * scale);
            Texture texture = textures.get(display.sourceTile());
            batch.draw(texture, x, y, size + 1f, size + 1f, display.u0(), display.v0(), display.u1(), display.v1());
        }
        batch.end();
    }

    private void drawTileDiagnostics() {
        ContinuumMapViewport.Frame frame = model.frame();
        long span = model.tileWorldSpan(frame.desiredLevel());
        double scale = model.pixelsPerWorldUnit();

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ContinuumMapViewport.DisplayTile display : frame.tiles()) {
            long worldX = Math.multiplyExact(display.targetKey().tileX(), span);
            long worldY = Math.multiplyExact(display.targetKey().tileY(), span);
            float x = (float) model.screenXForWorld(worldX);
            float y = (float) model.screenYForWorld(worldY);
            float size = (float) (span * scale);
            shapes.setColor(display.fallbackDepth() == 0 ? FINE_BORDER : FALLBACK_BORDER);
            shapes.rect(x, y, size, size);
        }
        shapes.end();
    }

    private void drawOverlay() {
        var frame = model.frame();
        var metrics = model.metrics();
        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(0.86f);
        font.setColor(TEXT);
        font.draw(batch, "STAGE 4 / MAP + ZOOM", 22f, height - 24f);

        font.getData().setScale(0.72f);
        font.setColor(MUTED);
        font.draw(batch,
                "drag mouse: move   wheel: zoom   Home: whole world   G: tile diagnostics   Esc: back",
                22f,
                27f);

        font.draw(batch,
                "center " + Math.round(model.centerX()) + ", " + Math.round(model.centerY())
                        + "   LOD L" + frame.desiredLevel()
                        + "   visible " + frame.visibleTileCount()
                        + "   detailed " + frame.exactReadyCount()
                        + "   temporary coarse " + frame.fallbackCount(),
                22f,
                height - 50f);

        if (showDiagnostics) {
            font.setColor(TEXT);
            font.draw(batch,
                    "CPU tiles " + metrics.residentTiles() + "/" + metrics.maxResidentTiles()
                            + "   GPU textures " + textures.size() + "/" + textures.maxEntries()
                            + "   requested " + frame.requestedWithPrefetch()
                            + "   pending " + metrics.pendingJobs()
                            + "   running " + metrics.runningJobs(),
                    22f,
                    height - 74f);
            font.setColor(FALLBACK_BORDER);
            font.draw(batch, "orange border = coarse parent temporarily filling a not-yet-ready detailed tile", 22f, height - 98f);
            font.setColor(FINE_BORDER);
            font.draw(batch, "green border = requested detail is ready", 22f, height - 120f);
        }
        batch.end();
    }

    private Texture createTexture(ContinuumMapTile tile) {
        int side = tile.sampleSide();
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        for (int y = 0; y < side; y++) {
            for (int x = 0; x < side; x++) {
                float value = tile.luminanceUnsigned(x, y) / 255f;
                float r = 0.08f + value * 0.62f;
                float g = 0.11f + value * 0.70f;
                float b = 0.16f + value * 0.76f;
                pixmap.setColor(r, g, b, 1f);
                pixmap.drawPixel(x, side - 1 - y);
            }
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private final class MapInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT) return false;
            dragging = true;
            lastDragX = screenX;
            lastDragY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            if (!dragging) return false;
            int dx = screenX - lastDragX;
            int dy = screenY - lastDragY;
            lastDragX = screenX;
            lastDragY = screenY;
            model.panPixels(dx, dy);
            return true;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.LEFT) dragging = false;
            return button == Input.Buttons.LEFT;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (amountY == 0f) return false;
            double factor = amountY < 0f ? 1.22d : 1d / 1.22d;
            model.zoomAt(factor, Gdx.input.getX(), Gdx.input.getY());
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.HOME -> model.fitWholeWorld();
                case Input.Keys.G -> showDiagnostics = !showDiagnostics;
                case Input.Keys.ESCAPE -> returnToMenu.run();
                default -> {
                    return false;
                }
            }
            return true;
        }
    }
}
