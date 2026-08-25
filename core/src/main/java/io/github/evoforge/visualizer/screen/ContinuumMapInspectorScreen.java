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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapTile;
import io.github.evoforge.simulation.world.continuum.map.ContinuumMapViewport;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsDefinition;
import io.github.evoforge.simulation.world.geophysics.MacroGeophysicsPreset;
import io.github.evoforge.simulation.world.terrain.TerrainSurfaceDefinition;
import io.github.evoforge.visualizer.continuum.BoundedRenderCache;
import io.github.evoforge.visualizer.continuum.ContinuumMapInspectorModel;
import io.github.evoforge.visualizer.continuum.TerrainSurface3DInspector;
import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

/** 2D world map and bounded 3D observer of the authoritative Stage 6 continuous Terrain surface. */
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

    private enum SurfaceViewMode {
        MAP_2D,
        TERRAIN_3D
    }

    private final Runnable returnToMenu;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final Vector2 uiPointer = new Vector2();
    private final BoundedRenderCache<ContinuumMapTile, Texture> textures =
            new BoundedRenderCache<>(MAX_GPU_TEXTURES, this::createTexture, Texture::dispose);
    private final InputAdapter mapInput = new MapInput();
    private final Stage uiStage = new Stage(new ScreenViewport());
    private final InputMultiplexer input;

    private final Label profileLabel = new Label("", skin);
    private final TextButton viewModeButton = new TextButton("", skin);
    private final TextField seedField = new TextField("", skin);
    private final Label seedStatus = new Label("", skin);

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

    private final Slider reliefSlider = normalizedSlider();
    private final Slider ruggednessSlider = normalizedSlider();
    private final Slider plateauSlider = normalizedSlider();
    private final Slider reliefScaleSlider = normalizedSlider();
    private final Label reliefValue = new Label("", skin);
    private final Label ruggednessValue = new Label("", skin);
    private final Label plateauValue = new Label("", skin);
    private final Label reliefScaleValue = new Label("", skin);

    private ContinuumMapInspectorModel model;
    private TerrainSurface3DInspector terrain3d;
    private Window settingsWindow;
    private SurfaceViewMode viewMode = SurfaceViewMode.MAP_2D;
    private int width = 1;
    private int height = 1;
    private boolean showDiagnostics;
    private boolean draggingMap;
    private boolean orbitingTerrain;
    private boolean panningTerrain;
    private int lastDragX;
    private int lastDragY;

    public ContinuumMapInspectorScreen(Runnable returnToMenu) {
        if (returnToMenu == null) throw new IllegalArgumentException("returnToMenu must not be null");
        this.returnToMenu = returnToMenu;
        this.width = Math.max(1, Gdx.graphics.getWidth());
        this.height = Math.max(1, Gdx.graphics.getHeight());
        this.model = ContinuumMapInspectorModel.standard(width, height);
        this.terrain3d = new TerrainSurface3DInspector(model);
        this.settingsWindow = createSettingsWindow();
        uiStage.addActor(settingsWindow);
        this.input = new InputMultiplexer(uiStage, mapInput);
        projection.setToOrtho2D(0f, 0f, width, height);
        syncControls(model.definition(), model.surfaceDefinition());
        syncSeedControl();
        updateViewButton();
        resize(width, height);
    }

    @Override
    public void show() {
        Gdx.input.setInputProcessor(input);
    }

    @Override
    public void render(float delta) {
        if (viewMode == SurfaceViewMode.MAP_2D) {
            model.update(width, height);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            drawMap();
            if (showDiagnostics) drawTileDiagnostics();
        } else {
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glClearColor(BACKGROUND.r, BACKGROUND.g, BACKGROUND.b, BACKGROUND.a);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
            terrain3d.render(width, height);
            Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        }

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
        terrain3d.close();
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
        window.defaults().pad(3f);

        window.add(viewModeButton).colspan(3).growX().height(30f);
        window.row();
        viewModeButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                toggleViewMode();
            }
        });

        window.add(profileLabel).colspan(3).left().growX();
        window.row();

        window.add(new Label("World seed", skin)).left();
        window.add(seedField).width(SETTINGS_SLIDER_WIDTH).growX();
        TextButton applySeed = new TextButton("Apply", skin);
        applySeed.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                applySeedFromField();
            }
        });
        window.add(applySeed).width(62f).height(28f);
        window.row();

        TextButton randomSeed = new TextButton("Random seed", skin);
        randomSeed.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                randomizeSeed();
            }
        });
        window.add(randomSeed).colspan(3).growX().height(28f);
        window.row();
        seedStatus.setColor(MUTED);
        window.add(seedStatus).colspan(3).left().growX();
        window.row();

        Label macroHeader = new Label("STAGE 5 / MACRO GEOPHYSICS", skin);
        macroHeader.setColor(MUTED);
        window.add(macroHeader).colspan(3).left().padTop(5f);
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

        Label surfaceHeader = new Label("STAGE 6 / CONTINUOUS SURFACE", skin);
        surfaceHeader.setColor(MUTED);
        window.add(surfaceHeader).colspan(3).left().padTop(7f);
        window.row();
        addSettingRow(window, "Relief intensity", reliefSlider, reliefValue);
        addSettingRow(window, "Regional ruggedness", ruggednessSlider, ruggednessValue);
        addSettingRow(window, "Plateau tendency", plateauSlider, plateauValue);
        addSettingRow(window, "Regional relief scale", reliefScaleSlider, reliefScaleValue);

        TextButton apply = new TextButton("Apply authored settings", skin);
        apply.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                applyCustomDefinitions();
            }
        });
        window.add(apply).colspan(3).growX().height(30f).padTop(8f);
        window.row();

        Label hint = new Label("Macro presets apply immediately; sliders apply together.", skin);
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
        reliefSlider.addListener(values);
        ruggednessSlider.addListener(values);
        plateauSlider.addListener(values);
        reliefScaleSlider.addListener(values);

        seedField.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode != Input.Keys.ENTER) return false;
                applySeedFromField();
                return true;
            }
        });

        window.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });

        window.pack();
        return window;
    }

    private void addPresetButton(Table table, String text, MacroGeophysicsPreset preset) {
        TextButton button = new TextButton(text, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                selectPreset(preset);
            }
        });
        table.add(button).width(130f).height(28f).pad(2f);
    }

    private void addSettingRow(Window window, String text, Slider slider, Label value) {
        window.add(new Label(text, skin)).left();
        window.add(slider).width(SETTINGS_SLIDER_WIDTH).growX();
        window.add(value).width(38f).right();
        window.row();
    }

    private Slider normalizedSlider() {
        return new Slider(0f, 1f, 0.01f, false, skin);
    }

    private void toggleViewMode() {
        if (viewMode == SurfaceViewMode.MAP_2D) {
            viewMode = SurfaceViewMode.TERRAIN_3D;
            terrain3d.centerOn(Math.round(model.centerX()), Math.round(model.centerY()));
        } else {
            viewMode = SurfaceViewMode.MAP_2D;
        }
        draggingMap = false;
        orbitingTerrain = false;
        panningTerrain = false;
        updateViewButton();
    }

    private void updateViewButton() {
        viewModeButton.setText(viewMode == SurfaceViewMode.MAP_2D
                ? "View: 2D world map  (switch to 3D)"
                : "View: 3D terrain  (switch to 2D)");
    }

    private void selectPreset(MacroGeophysicsPreset preset) {
        if (model.applyPreset(preset)) sourceChanged();
        syncControls(model.definition(), model.surfaceDefinition());
    }

    private void applySeedFromField() {
        try {
            applyWorldSeed(parseSeed(seedField.getText()));
            seedStatus.setColor(MUTED);
            seedStatus.setText("Seed applied");
        } catch (NumberFormatException invalid) {
            seedStatus.setColor(FALLBACK_BORDER);
            seedStatus.setText("Invalid seed");
        }
    }

    private void randomizeSeed() {
        long randomSeed = ThreadLocalRandom.current().nextLong(Long.MAX_VALUE);
        seedField.setText(Long.toString(randomSeed));
        applyWorldSeed(randomSeed);
        seedStatus.setColor(MUTED);
        seedStatus.setText("Random seed applied");
    }

    private void applyWorldSeed(long seed) {
        if (model.applySeed(seed)) sourceChanged();
        syncSeedControl();
    }

    static long parseSeed(String rawSeed) {
        if (rawSeed == null) throw new NumberFormatException("seed must not be null");
        String seed = rawSeed.trim().replace("_", "");
        if (seed.startsWith("0x") || seed.startsWith("0X")) {
            if (seed.length() == 2) throw new NumberFormatException("hex seed has no digits");
            return Long.parseUnsignedLong(seed.substring(2), 16);
        }
        return Long.parseLong(seed);
    }

    private void syncSeedControl() {
        seedField.setText(Long.toString(model.seed()));
    }

    private void applyCustomDefinitions() {
        MacroGeophysicsDefinition macro = MacroGeophysicsDefinition.of(
                oceanSlider.getValue(),
                scaleSlider.getValue(),
                cohesionSlider.getValue(),
                fragmentationSlider.getValue(),
                variationSlider.getValue());
        TerrainSurfaceDefinition surface = TerrainSurfaceDefinition.of(
                reliefSlider.getValue(),
                ruggednessSlider.getValue(),
                plateauSlider.getValue(),
                reliefScaleSlider.getValue());
        if (model.applyDefinitions(macro, surface)) sourceChanged();
        updateProfileLabel();
    }

    private void sourceChanged() {
        textures.clear();
        terrain3d.invalidateSurface();
    }

    private void syncControls(MacroGeophysicsDefinition macro, TerrainSurfaceDefinition surface) {
        oceanSlider.setValue((float) macro.oceanPrevalence().value());
        scaleSlider.setValue((float) macro.continentalScale().value());
        cohesionSlider.setValue((float) macro.landmassCohesion().value());
        fragmentationSlider.setValue((float) macro.fragmentation().value());
        variationSlider.setValue((float) macro.macroVariation().value());
        reliefSlider.setValue((float) surface.reliefIntensity().value());
        ruggednessSlider.setValue((float) surface.regionalRuggedness().value());
        plateauSlider.setValue((float) surface.plateauTendency().value());
        reliefScaleSlider.setValue((float) surface.regionalReliefScale().value());
        updateValueLabels();
        updateProfileLabel();
    }

    private void updateValueLabels() {
        oceanValue.setText(percent(oceanSlider.getValue()));
        scaleValue.setText(percent(scaleSlider.getValue()));
        cohesionValue.setText(percent(cohesionSlider.getValue()));
        fragmentationValue.setText(percent(fragmentationSlider.getValue()));
        variationValue.setText(percent(variationSlider.getValue()));
        reliefValue.setText(percent(reliefSlider.getValue()));
        ruggednessValue.setText(percent(ruggednessSlider.getValue()));
        plateauValue.setText(percent(plateauSlider.getValue()));
        reliefScaleValue.setText(percent(reliefScaleSlider.getValue()));
    }

    private void updateProfileLabel() {
        profileLabel.setText("Macro profile: " + model.profileName());
    }

    private void positionSettingsWindow() {
        float x = Math.max(SETTINGS_MARGIN, width - settingsWindow.getWidth() - SETTINGS_MARGIN);
        float y = Math.max(SETTINGS_MARGIN, height - settingsWindow.getHeight() - SETTINGS_MARGIN);
        settingsWindow.setPosition(x, y);
    }

    private boolean pointerOverSettings() {
        uiPointer.set(Gdx.input.getX(), Gdx.input.getY());
        uiStage.screenToStageCoordinates(uiPointer);
        return uiPointer.x >= settingsWindow.getX()
                && uiPointer.x <= settingsWindow.getX() + settingsWindow.getWidth()
                && uiPointer.y >= settingsWindow.getY()
                && uiPointer.y <= settingsWindow.getY() + settingsWindow.getHeight();
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
        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(0.86f);
        font.setColor(TEXT);
        font.draw(batch, "STAGE 6 / CONTINUOUS SURFACE EVOLUTION PROTOTYPE", 22f, height - 24f);

        font.getData().setScale(0.72f);
        font.setColor(MUTED);
        if (viewMode == SurfaceViewMode.MAP_2D) {
            var frame = model.frame();
            font.draw(batch,
                    "2D map   seed " + model.seed()
                            + "   geophysics r" + ContinuumMapInspectorModel.GEOPHYSICS_REVISION
                            + "   surface r" + ContinuumMapInspectorModel.SURFACE_REVISION
                            + "   center " + Math.round(model.centerX()) + ", " + Math.round(model.centerY())
                            + "   LOD L" + frame.desiredLevel(),
                    22f,
                    height - 50f);
            font.draw(batch,
                    "drag: move   wheel: zoom   Home: whole world   T/Tab: 3D terrain   G: diagnostics   Esc: back",
                    22f,
                    27f);

            if (showDiagnostics) drawMapDiagnosticsText(frame);
        } else {
            font.draw(batch,
                    "3D terrain   seed " + model.seed()
                            + "   center " + terrain3d.centerX() + ", " + terrain3d.centerY()
                            + "   sample step " + terrain3d.sampleStep()
                            + "   span " + terrain3d.sampledWorldSpan(),
                    22f,
                    height - 50f);
            font.draw(batch,
                    "left drag: orbit   right drag: pan   wheel: nested LOD   Home: reset   T/Tab: 2D map   Esc: back",
                    22f,
                    27f);
            font.setColor(FALLBACK_BORDER);
            font.draw(batch,
                    "inspection vertical exaggeration x" + Math.round(TerrainSurface3DInspector.VERTICAL_EXAGGERATION)
                            + "   (surface Z values themselves are unchanged)",
                    22f,
                    height - 74f);
        }
        batch.end();
    }

    private void drawMapDiagnosticsText(ContinuumMapViewport.Frame frame) {
        var metrics = model.metrics();
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
        font.draw(batch, "orange = temporary common coarse fallback", 22f, height - 122f);
        font.setColor(FINE_BORDER);
        font.draw(batch, "green border = requested detail ready", 22f, height - 144f);
    }

    private Texture createTexture(ContinuumMapTile tile) {
        int side = tile.sampleSide();
        byte[] luminance = tile.copyLuminance();
        Pixmap pixmap = new Pixmap(side, side, Pixmap.Format.RGBA8888);
        writeTexturePixels(luminance, side, pixmap.getPixels());

        Texture texture = new Texture(pixmap, true);
        pixmap.dispose();
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear);
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

    /**
     * Decodes the packed Terrain map byte. Height/depth selects the base hue; the low three bits
     * only modulate brightness. This keeps cartographic relief readable without recoloring ordinary
     * shaded land as rocky high terrain.
     */
    private static byte[] geophysicalPalette(int channel) {
        byte[] palette = new byte[256];
        for (int value = 0; value < palette.length; value++) {
            boolean land = (value & 0x80) != 0;
            int elevationBand = (value >>> 3) & 0x0F;
            int shadeBand = value & 0x07;
            float elevation = elevationBand / 15f;
            float shade = shadeBand / 7f;

            float[] base;
            float brightness;
            if (!land) {
                base = interpolateColor(
                        new float[] {0.17f, 0.43f, 0.62f},
                        new float[] {0.025f, 0.09f, 0.22f},
                        elevation);
                brightness = 0.90f + shade * 0.12f;
            } else {
                if (elevation < 0.34f) {
                    base = interpolateColor(
                            new float[] {0.20f, 0.43f, 0.19f},
                            new float[] {0.31f, 0.49f, 0.23f},
                            elevation / 0.34f);
                } else if (elevation < 0.64f) {
                    base = interpolateColor(
                            new float[] {0.31f, 0.49f, 0.23f},
                            new float[] {0.47f, 0.40f, 0.27f},
                            (elevation - 0.34f) / 0.30f);
                } else if (elevation < 0.86f) {
                    base = interpolateColor(
                            new float[] {0.47f, 0.40f, 0.27f},
                            new float[] {0.58f, 0.55f, 0.49f},
                            (elevation - 0.64f) / 0.22f);
                } else {
                    base = interpolateColor(
                            new float[] {0.58f, 0.55f, 0.49f},
                            new float[] {0.84f, 0.82f, 0.76f},
                            (elevation - 0.86f) / 0.14f);
                }
                brightness = 0.78f + shade * 0.32f;
            }
            palette[value] = (byte) Math.round(Math.min(1f, base[channel] * brightness) * 255f);
        }
        return palette;
    }

    private static float[] interpolateColor(float[] from, float[] to, float amount) {
        float bounded = Math.max(0f, Math.min(1f, amount));
        return new float[] {
            from[0] + (to[0] - from[0]) * bounded,
            from[1] + (to[1] - from[1]) * bounded,
            from[2] + (to[2] - from[2]) * bounded
        };
    }

    private static String percent(double normalized) {
        return Math.round(normalized * 100d) + "%";
    }

    private final class MapInput extends InputAdapter {
        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (viewMode == SurfaceViewMode.MAP_2D) {
                if (button != Input.Buttons.LEFT) return false;
                draggingMap = true;
            } else if (button == Input.Buttons.LEFT) {
                orbitingTerrain = true;
            } else if (button == Input.Buttons.RIGHT) {
                panningTerrain = true;
            } else {
                return false;
            }
            lastDragX = screenX;
            lastDragY = screenY;
            return true;
        }

        @Override
        public boolean touchDragged(int screenX, int screenY, int pointer) {
            int dx = screenX - lastDragX;
            int dy = screenY - lastDragY;
            lastDragX = screenX;
            lastDragY = screenY;

            if (viewMode == SurfaceViewMode.MAP_2D && draggingMap) {
                model.panPixels(dx, dy);
                return true;
            }
            if (viewMode == SurfaceViewMode.TERRAIN_3D && orbitingTerrain) {
                terrain3d.orbitPixels(dx, dy);
                return true;
            }
            if (viewMode == SurfaceViewMode.TERRAIN_3D && panningTerrain) {
                terrain3d.panPixels(dx, dy, width, height);
                return true;
            }
            return false;
        }

        @Override
        public boolean touchUp(int screenX, int screenY, int pointer, int button) {
            if (button == Input.Buttons.LEFT) {
                draggingMap = false;
                orbitingTerrain = false;
                return true;
            }
            if (button == Input.Buttons.RIGHT) {
                panningTerrain = false;
                return true;
            }
            return false;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            if (pointerOverSettings()) return true;
            if (amountY == 0f) return false;
            if (viewMode == SurfaceViewMode.MAP_2D) {
                double factor = amountY < 0f ? 1.22d : 1d / 1.22d;
                model.zoomAt(factor, Gdx.input.getX(), Gdx.input.getY());
            } else {
                terrain3d.zoom(amountY < 0f);
            }
            return true;
        }

        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.HOME -> {
                    if (viewMode == SurfaceViewMode.MAP_2D) model.fitWholeWorld();
                    else terrain3d.resetToMapCenter();
                }
                case Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> selectPreset(MacroGeophysicsPreset.SUPERCONTINENT);
                case Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> selectPreset(MacroGeophysicsPreset.BALANCED);
                case Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> selectPreset(MacroGeophysicsPreset.ARCHIPELAGO);
                case Input.Keys.NUM_4, Input.Keys.NUMPAD_4 -> selectPreset(MacroGeophysicsPreset.OCEANIC);
                case Input.Keys.G -> showDiagnostics = !showDiagnostics;
                case Input.Keys.T, Input.Keys.TAB -> toggleViewMode();
                case Input.Keys.ESCAPE -> returnToMenu.run();
                default -> {
                    return false;
                }
            }
            return true;
        }
    }
}
