package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Mouse-driven settings sidebar for the world-generation preview workspace. */
final class WorldGenerationSettingsPanel implements Disposable {
    private static final float PANEL_WIDTH = 360f;
    private static final float PANEL_MARGIN = 12f;
    private static final float CONTENT_PADDING = 18f;
    private static final float LABEL_WIDTH = 122f;
    private static final float VALUE_WIDTH = 64f;
    private static final float DIMENSION_FIELD_WIDTH = 92f;

    private final WorldGenerationPreviewSettings settings;
    private final Runnable generateAction;
    private final Stage stage = new Stage(new ScreenViewport());
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final Label statusLabel = new Label("Edit settings, then press Generate.", skin);
    private final TextField widthField;
    private final TextField lengthField;
    private final TextField seedField;
    private boolean twoDimensional;

    WorldGenerationSettingsPanel(
            WorldGenerationPreviewSettings settings,
            Runnable generateAction,
            boolean showSurface,
            boolean showOcean,
            boolean twoDimensional,
            int elevationTintPpm,
            Consumer<Boolean> surfaceVisibility,
            Consumer<Boolean> oceanVisibility,
            Consumer<Boolean> viewMode,
            IntConsumer elevationTint,
            IntConsumer meshDetail) {

        if (settings == null
                || generateAction == null
                || surfaceVisibility == null
                || oceanVisibility == null
                || viewMode == null
                || elevationTint == null
                || meshDetail == null) {
            throw new IllegalArgumentException("world-generation panel dependencies must not be null");
        }
        this.settings = settings;
        this.generateAction = generateAction;
        this.twoDimensional = twoDimensional;
        this.widthField = dimensionField(settings.width());
        this.lengthField = dimensionField(settings.length());
        this.seedField = new TextField(Long.toString(settings.seed()), skin);
        this.seedField.setAlignment(Align.center);
        addDirtyListener(seedField);

        Table rootContent = new Table(skin);
        rootContent.top().left();
        rootContent.pad(CONTENT_PADDING);
        rootContent.defaults().growX().minWidth(0f).padBottom(10f);

        Label title = new Label("WORLD GENERATION", skin, "window");
        rootContent.add(title).left().padBottom(4f);
        rootContent.row();
        Label subtitle = new Label("V13 structural mountains over accepted V12 base", skin, "subtitle");
        rootContent.add(subtitle).left().padBottom(10f);
        rootContent.row();

        Table worldTab = buildWorldTab(
                showSurface,
                showOcean,
                elevationTintPpm,
                surfaceVisibility,
                oceanVisibility,
                viewMode,
                elevationTint);
        Table performanceTab = buildPerformanceTab(meshDetail);

        TextButton worldButton = new TextButton("WORLD", skin);
        TextButton performanceButton = new TextButton("PERFORMANCE", skin);
        worldButton.setChecked(true);
        performanceButton.setChecked(false);

        Table tabRow = new Table(skin);
        tabRow.add(worldButton).growX().height(32f).padRight(4f);
        tabRow.add(performanceButton).growX().height(32f).padLeft(4f);
        rootContent.add(tabRow).growX().padBottom(8f);
        rootContent.row();

        Table tabHost = new Table(skin);
        tabHost.top().left();
        showTab(tabHost, worldTab, rootContent);
        rootContent.add(tabHost).growX().minWidth(0f).top();
        rootContent.row();

        worldButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                worldButton.setChecked(true);
                performanceButton.setChecked(false);
                showTab(tabHost, worldTab, rootContent);
                statusLabel.setText("World generation settings.");
            }
        });
        performanceButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                worldButton.setChecked(false);
                performanceButton.setChecked(true);
                showTab(tabHost, performanceTab, rootContent);
                statusLabel.setText("Performance settings apply live.");
            }
        });

        TextButton generate = new TextButton("GENERATE", skin);
        generate.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateFromControls();
            }
        });
        rootContent.add(generate).growX().minWidth(0f).height(38f).padTop(8f).padBottom(10f);
        rootContent.row();

        statusLabel.setWrap(true);
        rootContent.add(statusLabel).growX().minWidth(0f).left();
        rootContent.row();

        ScrollPane scroll = new ScrollPane(rootContent, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);
        scroll.setOverscroll(false, false);

        Table frame = new Table(skin);
        frame.setBackground(skin.getDrawable("list"));
        frame.setClip(true);
        frame.add(scroll).grow().minWidth(0f);

        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(frame).width(PANEL_WIDTH).growY().pad(PANEL_MARGIN);
        stage.addActor(root);
    }

    InputProcessor inputProcessor() {
        return stage;
    }

    boolean containsScreenPoint(int screenX, int screenY) {
        int screenWidth = Gdx.graphics.getWidth();
        int screenHeight = Gdx.graphics.getHeight();
        return screenX >= screenWidth - PANEL_MARGIN - PANEL_WIDTH
                && screenX <= screenWidth - PANEL_MARGIN
                && screenY >= PANEL_MARGIN
                && screenY <= screenHeight - PANEL_MARGIN;
    }

    float previewRightEdge() {
        return Gdx.graphics.getWidth() - PANEL_MARGIN - PANEL_WIDTH - PANEL_MARGIN;
    }

    boolean keyboardInputActive() {
        return stage.getKeyboardFocus() != null;
    }

    void render(float delta) {
        stage.act(Math.min(delta, 1f / 15f));
        stage.draw();
    }

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        skin.dispose();
    }

    private Table buildWorldTab(
            boolean showSurface,
            boolean showOcean,
            int elevationTintPpm,
            Consumer<Boolean> surfaceVisibility,
            Consumer<Boolean> oceanVisibility,
            Consumer<Boolean> viewMode,
            IntConsumer elevationTint) {
        Table content = tabContent();

        addSection(content, "WORLD");
        addDimensionControl(content, "Width", widthField);
        addDimensionControl(content, "Length", lengthField);
        addSeedControl(content);
        addRandomSeedControl(content);

        addSection(content, "BASE LAND SHAPE (V12)");
        addPercentControl(content, "Land", settings.coveragePpm(), settings::coveragePpm);
        addPercentControl(content, "Continent scale", settings.scalePpm(), settings::scalePpm);
        addPercentControl(content, "Fragmentation", settings.fragmentationPpm(), settings::fragmentationPpm);
        addPercentControl(content, "Macro height", settings.reliefPpm(), settings::reliefPpm);
        addPercentControl(content, "Rolling hills", settings.localReliefPpm(), settings::localReliefPpm);
        addPercentControl(content, "Landform size", settings.landformScalePpm(), settings::landformScalePpm);
        addPercentControl(content, "Ruggedness", settings.ruggednessPpm(), settings::ruggednessPpm);

        addSection(content, "MOUNTAINS (V13)");
        Label mountainHint = new Label(
                "Dedicated ridge systems are generated above the accepted V12 surface. High soft mountains automatically widen; steep or impassable faces are allowed.",
                skin,
                "subtitle");
        mountainHint.setWrap(true);
        content.add(mountainHint).growX().minWidth(0f).left().padBottom(10f);
        content.row();
        addPercentControl(content, "Abundance", settings.mountainAbundancePpm(), settings::mountainAbundancePpm);
        addPercentControl(content, "Height", settings.mountainHeightPpm(), settings::mountainHeightPpm);
        addPercentControl(content, "Scale", settings.mountainScalePpm(), settings::mountainScalePpm);
        addPercentControl(content, "Chaininess", settings.mountainChaininessPpm(), settings::mountainChaininessPpm);
        addPercentControl(content, "Peak sharpness", settings.mountainSharpnessPpm(), settings::mountainSharpnessPpm);
        addGenerationToggle(
                content,
                "Allow plateau mountains",
                settings.mountainPlateausEnabled(),
                settings::mountainPlateausEnabled);
        addPercentControl(
                content,
                "Plateau chance",
                settings.mountainPlateauProbabilityPpm(),
                settings::mountainPlateauProbabilityPpm);

        addSection(content, "PREVIEW");
        addViewModeControl(content, viewMode);
        addLivePercentControl(content, "Z contrast", elevationTintPpm, elevationTint);
        addVisibilityControl(content, "Terrain surface", showSurface, surfaceVisibility);
        addVisibilityControl(content, "Ocean water", showOcean, oceanVisibility);
        return content;
    }

    private Table buildPerformanceTab(IntConsumer meshDetail) {
        Table content = tabContent();
        addSection(content, "2D LOD QUALITY");

        Label explanation = new Label(
                "Live presentation-only tuning. Higher values keep more terrain samples and look smoother from far away, but cost more FPS.",
                skin,
                "subtitle");
        explanation.setWrap(true);
        content.add(explanation).growX().minWidth(0f).left().padBottom(12f);
        content.row();

        Slider detail = addBudgetControl(
                content,
                "Detailed range",
                WorldGeneration2DLod.MIN_DETAILED_CELLS,
                WorldGeneration2DLod.MAX_DETAILED_CELLS,
                500L,
                WorldGeneration2DLod.detailedCellBudget(),
                WorldGeneration2DLod::detailedCellBudget);

        Slider overview = addBudgetControl(
                content,
                "Far detail",
                WorldGeneration2DLod.MIN_OVERVIEW_SAMPLES,
                WorldGeneration2DLod.MAX_OVERVIEW_SAMPLES,
                500L,
                WorldGeneration2DLod.overviewSampleBudget(),
                WorldGeneration2DLod::overviewSampleBudget);

        Label detailHint = new Label(
                "Raise Far detail first for rounder coastlines and less compressed distant terrain. Detailed range only controls how long exact LOD x1 is retained.",
                skin,
                "subtitle");
        detailHint.setWrap(true);
        content.add(detailHint).growX().minWidth(0f).left().padTop(6f).padBottom(12f);
        content.row();

        addSection(content, "3D MESH QUALITY");
        Label meshExplanation = new Label(
                "3D mesh axis is the maximum number of terrain samples along each world axis. Large worlds use 160 by default; increasing it preserves much more relief shape.",
                skin,
                "subtitle");
        meshExplanation.setWrap(true);
        content.add(meshExplanation).growX().minWidth(0f).left().padBottom(12f);
        content.row();

        Slider mesh = addIntControl(
                content,
                "3D mesh axis",
                WorldGeneration3DDetail.MIN_AXIS_SAMPLES,
                WorldGeneration3DDetail.MAX_AXIS_SAMPLES,
                16,
                WorldGeneration3DDetail.maxAxisSamples(),
                meshDetail);

        Label meshHint = new Label(
                "160 = fast default. Try 256-384 for a much more organic large-world mesh; 512 is the high-quality inspection limit. Changes rebuild only preview mesh chunks.",
                skin,
                "subtitle");
        meshHint.setWrap(true);
        content.add(meshHint).growX().minWidth(0f).left().padTop(6f).padBottom(12f);
        content.row();

        TextButton reset = new TextButton("RESET PERFORMANCE DEFAULTS", skin);
        reset.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                WorldGeneration2DLod.resetTuning();
                detail.setValue(WorldGeneration2DLod.detailedCellBudget());
                overview.setValue(WorldGeneration2DLod.overviewSampleBudget());
                meshDetail.accept(WorldGeneration3DDetail.DEFAULT_MAX_AXIS_SAMPLES);
                mesh.setValue(WorldGeneration3DDetail.maxAxisSamples());
                statusLabel.setText("Performance defaults restored. Applied live.");
            }
        });
        content.add(reset).growX().height(34f).padTop(4f);
        content.row();

        Label defaults = new Label(
                "Fast defaults: 9k detailed / 6k far samples / 160 3D mesh axis.",
                skin,
                "subtitle");
        defaults.setWrap(true);
        content.add(defaults).growX().minWidth(0f).left().padTop(8f);
        content.row();
        return content;
    }

    private static void showTab(Table host, Table content, Table rootContent) {
        host.clearChildren();
        host.add(content).growX().minWidth(0f).top();
        host.invalidateHierarchy();
        rootContent.invalidateHierarchy();
    }

    private Table tabContent() {
        Table content = new Table(skin);
        content.top().left();
        content.defaults().growX().minWidth(0f).padBottom(10f);
        return content;
    }

    private TextField dimensionField(int initialValue) {
        TextField field = new TextField(Integer.toString(initialValue), skin);
        field.setAlignment(Align.center);
        field.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        field.setMaxLength(Integer.toString(WorldGenerationPreviewSettings.MAX_HORIZONTAL_DIMENSION).length());
        addDirtyListener(field);
        return field;
    }

    private void addDimensionControl(Table content, String name, TextField field) {
        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(field).width(DIMENSION_FIELD_WIDTH).height(30f);
        row.add(new Label("cells", skin, "subtitle")).left().padLeft(8f);
        row.add().expandX();
        content.add(row).growX().minWidth(0f);
        content.row();
    }

    private void addSeedControl(Table content) {
        TextButton nextSeed = new TextButton("NEXT", skin);
        nextSeed.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.nextSeed();
                seedField.setText(Long.toString(settings.seed()));
                markDirty();
            }
        });

        Table row = new Table(skin);
        row.add(new Label("Seed", skin)).width(LABEL_WIDTH).left();
        row.add(seedField).growX().minWidth(0f).height(30f);
        row.add(nextSeed).width(64f).height(30f).padLeft(8f);
        content.add(row).growX().minWidth(0f);
        content.row();
    }

    private void addRandomSeedControl(Table content) {
        CheckBox randomSeed = new CheckBox("", skin);
        randomSeed.setChecked(settings.randomSeedOnGenerate());
        randomSeed.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                settings.randomSeedOnGenerate(randomSeed.isChecked());
                markDirty();
            }
        });

        Table row = new Table(skin);
        row.add(randomSeed).left();
        row.add(new Label("Random seed on Generate", skin)).left().padLeft(8f);
        row.add().expandX();
        content.add(row).growX().minWidth(0f).left().padBottom(4f);
        content.row();
    }

    private void addGenerationToggle(
            Table content,
            String name,
            boolean initialValue,
            Consumer<Boolean> setter) {
        CheckBox checkBox = new CheckBox("", skin);
        checkBox.setChecked(initialValue);
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                setter.accept(checkBox.isChecked());
                markDirty();
            }
        });

        Table row = new Table(skin);
        row.add(checkBox).left();
        row.add(new Label(name, skin)).left().padLeft(8f);
        row.add().expandX();
        content.add(row).growX().minWidth(0f).left().padBottom(4f);
        content.row();
    }

    private void addPercentControl(Table content, String name, int initialPpm, IntConsumer setter) {
        addPercentControl(content, name, initialPpm, setter, true);
    }

    private void addLivePercentControl(Table content, String name, int initialPpm, IntConsumer setter) {
        addPercentControl(content, name, initialPpm, setter, false);
    }

    private void addPercentControl(
            Table content,
            String name,
            int initialPpm,
            IntConsumer setter,
            boolean marksGenerationDirty) {
        Slider slider = new Slider(0f, 100f, 1f, false, skin);
        slider.setValue(initialPpm / 10_000f);
        Label value = new Label(formatPercent(initialPpm), skin);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int ppm = Math.round(slider.getValue()) * 10_000;
                setter.accept(ppm);
                value.setText(formatPercent(ppm));
                if (marksGenerationDirty) markDirty();
            }
        });

        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(slider).growX().minWidth(80f);
        row.add(value).width(VALUE_WIDTH).right().padLeft(8f);
        content.add(row).growX().minWidth(0f);
        content.row();
    }

    private Slider addBudgetControl(
            Table content,
            String name,
            long minimum,
            long maximum,
            long step,
            long initial,
            java.util.function.LongConsumer setter) {
        Slider slider = new Slider(minimum, maximum, step, false, skin);
        slider.setValue(initial);
        Label value = new Label(formatBudget(initial), skin);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                long selected = Math.round(slider.getValue() / step) * step;
                setter.accept(selected);
                value.setText(formatBudget(selected));
                statusLabel.setText("Performance tuning applied live.");
            }
        });

        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(slider).growX().minWidth(80f);
        row.add(value).width(VALUE_WIDTH).right().padLeft(8f);
        content.add(row).growX().minWidth(0f);
        content.row();
        return slider;
    }

    private Slider addIntControl(
            Table content,
            String name,
            int minimum,
            int maximum,
            int step,
            int initial,
            IntConsumer setter) {
        Slider slider = new Slider(minimum, maximum, step, false, skin);
        slider.setValue(initial);
        Label value = new Label(Integer.toString(initial), skin);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int selected = Math.round(slider.getValue());
                setter.accept(selected);
                value.setText(Integer.toString(selected));
                statusLabel.setText("3D preview mesh rebuilt at " + selected + " samples/axis.");
            }
        });

        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(slider).growX().minWidth(80f);
        row.add(value).width(VALUE_WIDTH).right().padLeft(8f);
        content.add(row).growX().minWidth(0f);
        content.row();
        return slider;
    }

    private void addViewModeControl(Table content, Consumer<Boolean> viewMode) {
        TextButton mode = new TextButton(viewModeLabel(), skin);
        mode.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                twoDimensional = !twoDimensional;
                mode.setText(viewModeLabel());
                viewMode.accept(twoDimensional);
            }
        });
        content.add(mode).growX().minWidth(0f).height(32f).padBottom(6f);
        content.row();
    }

    private String viewModeLabel() {
        return twoDimensional ? "VIEW: 2D" : "VIEW: 3D";
    }

    private void addVisibilityControl(
            Table content,
            String name,
            boolean initialValue,
            Consumer<Boolean> visibility) {
        CheckBox checkBox = new CheckBox("", skin);
        checkBox.setChecked(initialValue);
        checkBox.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                visibility.accept(checkBox.isChecked());
            }
        });

        Table row = new Table(skin);
        row.add(checkBox).left();
        row.add(new Label(name, skin)).left().padLeft(8f);
        row.add().expandX();
        content.add(row).growX().minWidth(0f).left().padBottom(4f);
        content.row();
    }

    private void addSection(Table content, String title) {
        Label label = new Label(title, skin, "subtitle");
        content.add(label).left().padTop(10f).padBottom(8f);
        content.row();
    }

    private void generateFromControls() {
        Integer width = parseDimension("Width", widthField);
        if (width == null) return;
        Integer length = parseDimension("Length", lengthField);
        if (length == null) return;

        settings.width(width);
        settings.length(length);

        if (settings.randomSeedOnGenerate()) {
            settings.prepareSeedForGeneration(() -> ThreadLocalRandom.current().nextLong());
        } else {
            String seedText = seedField.getText().trim();
            long seed;
            try {
                seed = Long.parseLong(seedText);
            } catch (NumberFormatException exception) {
                statusLabel.setText("Seed must be a signed 64-bit integer.");
                return;
            }
            settings.seed(seed);
        }

        widthField.setText(Integer.toString(settings.width()));
        lengthField.setText(Integer.toString(settings.length()));
        seedField.setText(Long.toString(settings.seed()));

        generateAction.run();
        stage.setKeyboardFocus(null);
        stage.setScrollFocus(null);
        statusLabel.setText("Generated with seed " + settings.seed() + ".");
    }

    private Integer parseDimension(String name, TextField field) {
        String text = field.getText().trim();
        try {
            int value = Integer.parseInt(text);
            if (value < WorldGenerationPreviewSettings.MIN_HORIZONTAL_DIMENSION
                    || value > WorldGenerationPreviewSettings.MAX_HORIZONTAL_DIMENSION) {
                throw new NumberFormatException("dimension outside supported range");
            }
            return value;
        } catch (NumberFormatException exception) {
            statusLabel.setText(String.format(
                    "%s must be an integer from %d to %d cells.",
                    name,
                    WorldGenerationPreviewSettings.MIN_HORIZONTAL_DIMENSION,
                    WorldGenerationPreviewSettings.MAX_HORIZONTAL_DIMENSION));
            return null;
        }
    }

    private void addDirtyListener(TextField field) {
        field.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                markDirty();
            }
        });
    }

    private void markDirty() {
        statusLabel.setText("Pending changes - press Generate.");
    }

    private static String formatPercent(int ppm) {
        return Math.round(ppm / 10_000f) + "%";
    }

    private static String formatBudget(long value) {
        if (value >= 1_000L) {
            float thousands = value / 1_000f;
            return thousands == Math.round(thousands)
                    ? Math.round(thousands) + "k"
                    : String.format("%.1fk", thousands);
        }
        return Long.toString(value);
    }
}
