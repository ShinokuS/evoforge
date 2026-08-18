package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
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
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Mouse-driven settings sidebar for the world-generation preview workspace. */
final class WorldGenerationSettingsPanel implements Disposable {
    private static final float PANEL_WIDTH = 360f;
    private static final float PANEL_MARGIN = 12f;
    private static final float CONTENT_PADDING = 16f;
    private static final float LABEL_WIDTH = 98f;
    private static final float VALUE_WIDTH = 46f;

    private final WorldGenerationPreviewSettings settings;
    private final Runnable generateAction;
    private final Stage stage = new Stage(new ScreenViewport());
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final Label statusLabel = new Label("Edit settings, then press Generate.", skin);
    private final TextField widthField;
    private final TextField lengthField;
    private final TextField seedField;

    WorldGenerationSettingsPanel(
            WorldGenerationPreviewSettings settings,
            Runnable generateAction,
            boolean showSurface,
            boolean showOcean,
            Consumer<Boolean> surfaceVisibility,
            Consumer<Boolean> oceanVisibility) {

        if (settings == null
                || generateAction == null
                || surfaceVisibility == null
                || oceanVisibility == null) {
            throw new IllegalArgumentException("world-generation panel dependencies must not be null");
        }
        this.settings = settings;
        this.generateAction = generateAction;
        this.widthField = dimensionField(settings.width());
        this.lengthField = dimensionField(settings.length());
        this.seedField = new TextField(Long.toString(settings.seed()), skin);
        addDirtyListener(seedField);

        Table content = new Table(skin);
        content.top().left();
        content.pad(CONTENT_PADDING);
        content.defaults().growX().minWidth(0f).padBottom(8f);

        Label title = new Label("WORLD GENERATION", skin, "window");
        content.add(title).left().padBottom(4f);
        content.row();
        Label subtitle = new Label("V10 macro morphology", skin, "subtitle");
        content.add(subtitle).left().padBottom(14f);
        content.row();

        addSection(content, "WORLD");
        addTextControl(content, "Width", widthField);
        addTextControl(content, "Length", lengthField);
        addSeedControl(content);

        addSection(content, "LAND SHAPE");
        addPercentControl(content, "Land", settings.coveragePpm(), settings::coveragePpm);
        addPercentControl(content, "Scale", settings.scalePpm(), settings::scalePpm);
        addPercentControl(
                content,
                "Fragmentation",
                settings.fragmentationPpm(),
                settings::fragmentationPpm);
        addPercentControl(content, "Relief", settings.reliefPpm(), settings::reliefPpm);

        addSection(content, "PREVIEW");
        CheckBox surface = new CheckBox(" Surface mesh", skin);
        surface.setChecked(showSurface);
        surface.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                surfaceVisibility.accept(surface.isChecked());
            }
        });
        content.add(surface).left();
        content.row();

        CheckBox ocean = new CheckBox(" Ocean plane", skin);
        ocean.setChecked(showOcean);
        ocean.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                oceanVisibility.accept(ocean.isChecked());
            }
        });
        content.add(ocean).left().padBottom(14f);
        content.row();

        TextButton generate = new TextButton("GENERATE", skin);
        generate.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateFromControls();
            }
        });
        content.add(generate).growX().minWidth(0f).height(38f).padTop(6f).padBottom(10f);
        content.row();

        statusLabel.setWrap(true);
        content.add(statusLabel).growX().minWidth(0f).left();
        content.row();

        ScrollPane scroll = new ScrollPane(content, skin);
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

    private TextField dimensionField(int initialValue) {
        TextField field = new TextField(Integer.toString(initialValue), skin);
        field.setTextFieldFilter(new TextField.TextFieldFilter.DigitsOnlyFilter());
        field.setMaxLength(Integer.toString(WorldGenerationPreviewSettings.MAX_HORIZONTAL_DIMENSION).length());
        addDirtyListener(field);
        return field;
    }

    private void addTextControl(Table content, String name, TextField field) {
        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(field).growX().minWidth(0f).height(28f);
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
        row.add(seedField).growX().minWidth(0f).height(28f);
        row.add(nextSeed).width(64f).height(28f).padLeft(6f);
        content.add(row).growX().minWidth(0f);
        content.row();
    }

    private void addPercentControl(
            Table content,
            String name,
            int initialPpm,
            IntConsumer setter) {

        Slider slider = new Slider(0f, 100f, 1f, false, skin);
        slider.setValue(initialPpm / 10_000f);
        Label value = new Label(formatPercent(initialPpm), skin);
        slider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                int ppm = Math.round(slider.getValue()) * 10_000;
                setter.accept(ppm);
                value.setText(formatPercent(ppm));
                markDirty();
            }
        });

        Table row = new Table(skin);
        row.add(new Label(name, skin)).width(LABEL_WIDTH).left();
        row.add(slider).growX().minWidth(80f);
        row.add(value).width(VALUE_WIDTH).right().padLeft(8f);
        content.add(row).growX().minWidth(0f);
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

        String seedText = seedField.getText().trim();
        long seed;
        try {
            seed = Long.parseLong(seedText);
        } catch (NumberFormatException exception) {
            statusLabel.setText("Seed must be a signed 64-bit integer.");
            return;
        }

        settings.width(width);
        settings.length(length);
        settings.seed(seed);
        widthField.setText(Integer.toString(settings.width()));
        lengthField.setText(Integer.toString(settings.length()));
        seedField.setText(Long.toString(settings.seed()));

        generateAction.run();
        stage.setKeyboardFocus(null);
        statusLabel.setText("Generated from current settings.");
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
}
