package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.InputListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import io.github.evoforge.visualizer.continuum.BoundedRenderCache;
import io.github.evoforge.visualizer.continuum.ContinuumMapInspectorModel;
import java.nio.ByteBuffer;

/** World-oriented pan/zoom view of the Stage 5 macro-geophysical skeleton. */
public final class ContinuumMapInspectorScreen extends ScreenAdapter {
    private static final Color BACKGROUND = new Color(0.025f, 0.032f, 0.038f, 1f);
    private static final Color FINE_BORDER = new Color(0.30f, 0.86f, 0.65f, 0.9f);
    private static final Color FALLBACK_BORDER = new Color(1.00f, 0.72f, 0.24f, 0.95f);
    private static final Color TEXT = new Color(0.94f, 0.96f, 0.97f, 1f);
    private static final Color MUTED = new Color(0.66f, 0.71f, 0.74f, 1f);
    private static final int MAX_GPU_TEXTURES = 192;
    private static final float SETTINGS_MARGIN = 14f;
    private static final float SETTINGS_SLIDER_WIDTH = 150f;
    private static final byte[] PALETTE_R = geophysicalPalette(0);
    private static final byte[] PALETTE_G = geophysicalPalette(1);
    private static final byte[] PALETTE_B = geophysicalPalette(2);

    private final Runnable returnToMenu;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final BoundedRenderCache<ContinuumMapTile, Texture> textures =
            new BoundedRenderCache<>(MAX_GPU_TEXTURES, this::createTexture, Texture::dispose);
    private final InputAdapter mapInput = new MapInput();
    private final Stage uiStage = new Stage(new ScreenViewport());
    private final Window settingsWindow;
    private final Label profileLabel = new Label("", skin);
    private final Slider oceanSlider = normalizedSlider();
    private final Slider scaleSlider = normalizedSlider();
    private final Slider cohesionSlider = normalizedSlider();
    private final Slider fragmentationSlider = normalizedSlider();
    private final Slider variationSlider = normalizedSlider();
    private final Label oceanValue = new Label("", skin);
    private final Label scaleValue = new Label("", skin);
    private final Label cohesionValue = new Label("", skin);
    private final Label fragmentationValue = new Label("", skin);
    private final Label variationValue = new Label("", skin);
    private final InputMultiplexer input;

    private ContinuumMapInspectorModel model;
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
        this.settingsWindow = createSettingsWindow();
        uiStage.addActor(settingsWindow);
        uiStage.setScrollFocus(settingsWindow);
        this.input = new InputMultiplexer(uiStage, mapInput);
        projection.setToOrtho2D(0f, 0f, width, height);
        syncControls(model.definition());
        resize(width, height);
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
        uiStage.act(delta);
        uiStage.draw();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
        model.update(width, height);
        uiStage.getViewport().update(width, height, true);
        positionSettingsWindow();
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
        uiStage.dispose();
        shapes.dispose();
        batch.dispose();
        skin.dispose();
    }

    private Window createSettingsWindow() {
        Window window = new Window("WORLD GENERATION", skin);
        window.setMovable(false);
        window.setResizable(false);
        window.pad(30f, 14f, 14f, 14f);
        window.defaults().pad(4f);

        window.add(profileLabel).colspan(3).left().growX();
        window.row();

        Table presets = new Table();
        addPresetButton(presets, "Supercontinent", MacroGeophysicsPreset.SUPERCONTINENT);
        addPresetButton(presets, "Balanced", MacroGeophysicsPreset.BALANCED);
        presets.row();
        addPresetButton(presets, "Archipelago", MacroGeophysicsPreset.ARCHIPELAGO);
        addPresetButton(presets, "Oceanic", MacroGeophysicsPreset.OCEANIC);
        window.add(presets).colspan(3).growX();
        window.row();

        addSettingRow(window, "Ocean prevalence", oceanSlider, oceanValue);
        addSettingRow(window, "Continental scale", scaleSlider, scaleValue);
        addSettingRow(window, "Landmass cohesion", cohesionSlider, cohesionValue);
        addSettingRow(window, "Fragmentation", fragmentationSlider, fragmentationValue);
        addSettingRow(window, "Macro variation", variationSlider, variationValue);

        TextButton apply = new TextButton("Apply custom", skin);
        apply.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                applyCustomDefinition();
            }
        });
        window.add(apply).colspan(3).growX().height(30f).padTop(8f);
        window.row();

        Label hint = new Label("Presets apply immediately. Sliders apply on button.", skin);
        hint.setColor(MUTED);
        window.add(hint).colspan(3).left().padTop(4f);

        ChangeListener values = new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                updateValueLabels();
            }
        };
        oceanSlider.addListener(values);
        scaleSlider.addListener(values);
        cohesionSlider.addListener(values);
        fragmentationSlider.addListener(values);
        variationSlider.addListener(values);

        // The settings block is intentionally an input boundary. Empty panel space, sliders and
        // buttons must not also pan/zoom the underlying map.
        window.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }

            @Override
            public boolean scrolled(
                    InputEvent event,
                    float x,
                    float y,
                    float amountX,
                    float amountY) {
                return true;
            }
        });

        window.pack();
        return window;
    }

    private void addPresetButton(
            Table table,
            String text,
            MacroGeophysicsPreset preset) {
        TextButton button = new TextButton(text, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                selectPreset(preset);
            }
        });
        table.add(button).width(130f).height(28f).pad(2f);
    }

    private static void addSettingRow(
            Window window,
            String text,
            Slider slider,
            Label value) {
        window.add(new Label(text, window.getSkin())).left();
        window.add(slider).width(SETTINGS_SLIDER_WIDTH).growX();
        window.add(value).width(38f).right();
        window.row();
    }

    private Slider normalizedSlider() {
        return new Slider(0f, 1f, 0.01f, false, skin);
    }

    private void selectPreset(MacroGeophysicsPreset preset) {
        boolean changed = model.applyPreset(preset);
        if (changed) textures.clear();
        syncControls(model.definition());
    }

    private void applyCustomDefinition() {
        MacroGeophysicsDefinition custom = MacroGeophysicsDefinition.of(
                oceanSlider.getValue(),
                scaleSlider.getValue(),
                cohesionSlider.getValue(),
                fragmentationSlider.getValue(),
                variationSlider.getValue());
        boolean changed = model.applyDefinition(custom);
        if (changed) textures.clear();
        updateProfileLabel();
    }

    private void syncControls(MacroGeophysicsDefinition definition) {
        oceanSlider.setValue((float) definition.oceanPrevalence().value());
        scaleSlider.setValue((float) definition.continentalScale().value());
        cohesionSlider.setValue((float) definition.landmassCohesion().value());
        fragmentationSlider.setValue((float) definition.fragmentation().value());
        variationSlider.setValue((float) definition.macroVariation().value());
        updateValueLabels();
        updateProfileLabel();
    }

    private void updateValueLabels() {
        oceanValue.setText(percent(oceanSlider.getValue()));
        scaleValue.setText(percent(scaleSlider.getValue()));
        cohesionValue.setText(percent(cohesionSlider.getValue()));
        fragmentationValue.setText(percent(fragmentationSlider.getValue()));
        variationValue.setText(percent(variationSlider.getValue()));
    }

    private void updateProfileLabel() {
        profileLabel.setText("Macro profile: " + model.profileName());
    }

    private void positionSettingsWindow() {
        float x = Math.max(SETTINGS_MARGIN, width - settingsWindow.getWidth() - SETTINGS_MARGIN);
        float y = Math.max(SETTINGS_MARGIN, height - settingsWindow.getHeight() - SETTINGS_MARGIN);
        settingsWindow.setPosition(x, y);
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
        font.draw(batch, "STAGE 5 / MACRO OCEAN + GEOPHYSICAL SKELETON", 22f, height - 24f);

        font.getData().setScale(0.72f);
        font.setColor(MUTED);
        font.draw(batch,
                "seed " + ContinuumMapInspectorModel.WORLD_SEED
                        + "   revision " + ContinuumMapInspectorModel.GEOPHYSICS_REVISION
                        + "   center " + Math.round(model.centerX()) + ", " + Math.round(model.centerY())
                        + "   LOD L" + frame.desiredLevel(),
                22f,
                height - 50f);

        font.draw(batch,
                "drag: move   wheel: zoom   Home: whole world   G: diagnostics   Esc: back",
                22f,
                27f);

        if (showDiagnostics) {
            font.setColor(TEXT);
            font.draw(batch,
                    "visible " + frame.visibleTileCount()
                            + "   detailed " + frame.exactReadyCount()
                            + "   temporary coarse " + frame.fallbackCount()
                            + "   CPU tiles " + metrics.residentTiles() + "/" + metrics.maxResidentTiles()
                            + "   GPU textures " + textures.size() + "/" + textures.maxEntries(),
                    22f,
                    height - 74f);
            font.setColor(MUTED);
            font.draw(batch,
                    "visible queue " + metrics.visiblePendingJobs()
                            + "   prefetch queue " + metrics.prefetchPendingJobs()
                            + "   running " + metrics.runningJobs(),
                    22f,
                    height - 98f);
            font.setColor(FALLBACK_BORDER);
            font.draw(batch,
                    "orange = temporary coarse parent fallback",
                    22f,
                    height - 122f);
            font.setColor(FINE_BORDER);
            font.draw(batch, "green border = requested detail ready", 22f, height - 144f);
        }
        batch.end();
    }

    private Texture createTexture(ContinuumMapTile tile) {
        int side = tile.sampleSide();
        byte[] luminance = tile.copyLuminance();
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        writeTexturePixels(luminance, side, pixmap.getPixels());

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        texture.setWrap(Texture.TextureWrap.ClampToEdge, Texture.TextureWrap.ClampToEdge);
        return texture;
    }

    static void writeTexturePixels(byte[] luminance, int side, ByteBuffer pixels) {
        if (side <= 0) throw new IllegalArgumentException("side must be > 0");
        if (luminance == null || luminance.length != Math.multiplyExact(side, side)) {
            throw new IllegalArgumentException("luminance must contain exactly side*side samples");
        }
        if (pixels == null || pixels.capacity() < Math.multiplyExact(luminance.length, 4)) {
            throw new IllegalArgumentException("pixel buffer is too small");
        }

        int rowBytes = side * 4;
        for (int sourceY = 0; sourceY < side; sourceY++) {
            int sourceRow = sourceY * side;
            int destinationRow = sourceY * rowBytes;
            for (int x = 0; x < side; x++) {
                int value = Byte.toUnsignedInt(luminance[sourceRow + x]);
                int pixel = destinationRow + x * 4;
                pixels.put(pixel, PALETTE_R[value]);
                pixels.put(pixel + 1, PALETTE_G[value]);
                pixels.put(pixel + 2, PALETTE_B[value]);
                pixels.put(pixel + 3, (byte) 0xFF);
            }
        }
    }

    private static byte[] geophysicalPalette(int channel) {
        byte[] palette = new byte[256];
        for (int value = 0; value < palette.length; value++) {
            float[] start;
            float[] end;
            float amount;
            if (value < 128) {
                start = new float[] {0.025f, 0.09f, 0.24f};
                end = new float[] {0.18f, 0.48f, 0.68f};
                amount = value / 127f;
            } else {
                start = new float[] {0.20f, 0.48f, 0.23f};
                end = new float[] {0.78f, 0.72f, 0.58f};
                amount = (value - 128) / 127f;
            }
            palette[value] = (byte) Math.round((start[channel] + (end[channel] - start[channel]) * amount) * 255f);
        }
        return palette;
    }

    private static String percent(double normalized) {
        return Math.round(normalized * 100d) + "%";
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
                case Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> selectPreset(MacroGeophysicsPreset.SUPERCONTINENT);
                case Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> selectPreset(MacroGeophysicsPreset.BALANCED);
                case Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> selectPreset(MacroGeophysicsPreset.ARCHIPELAGO);
                case Input.Keys.NUM_4, Input.Keys.NUMPAD_4 -> selectPreset(MacroGeophysicsPreset.OCEANIC);
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
