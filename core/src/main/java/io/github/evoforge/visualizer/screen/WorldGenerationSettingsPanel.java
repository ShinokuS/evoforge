package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
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
    private static final float PANEL_WIDTH = 390f;

    private final WorldGenerationPreviewSettings settings;
    private final Runnable generateAction;
    private final Stage stage = new Stage(new ScreenViewport());
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final Label statusLabel = new Label("Edit settings, then press Generate.", skin);
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
        this.seedField = new TextField(Long.toString(settings.seed()), skin);

        Table content = new Table(skin);
        content.top().left();
        content.pad(18f);
        content.defaults().left().padBottom(8f);

        Label title = new Label("WORLD GENERATION", skin, "window");
        content.add(title).colspan(3).left().padBottom(4f);
        content.row();
        Label subtitle = new Label("V10 macro morphology", skin, "subtitle");
        content.add(subtitle).colspan(3).left().padBottom(14f);
        content.row();

        addSection(content, "WORLD");
        addDimensionControl(content, "Width", true);
        addDimensionControl(content, "Height", false);
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
        content.add(surface).colspan(3).left();
        content.row();

        CheckBox ocean = new CheckBox(" Ocean plane", skin);
        ocean.setChecked(showOcean);
        ocean.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                oceanVisibility.accept(ocean.isChecked());
            }
        });
        content.add(ocean).colspan(3).left().padBottom(14f);
        content.row();

        TextButton generate = new TextButton("GENERATE", skin);
        generate.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                generateFromControls();
            }
        });
        content.add(generate).colspan(3).growX().height(38f).padTop(6f).padBottom(10f);
        content.row();

        statusLabel.setWrap(true);
        content.add(statusLabel).colspan(3).growX().left();
        content.row();

        ScrollPane scroll = new ScrollPane(content, skin, "list");
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        Table root = new Table();
        root.setFillParent(true);
        root.top().right();
        root.add(scroll).width(PANEL_WIDTH).growY().pad(12f);
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

    private void addDimensionControl(Table content, String name, boolean width) {
        Label label = new Label(name, skin);
        SelectBox<Integer> box = new SelectBox<>(skin);
        box.setItems(settings.horizontalPresets());
        box.setSelected(width ? settings.width() : settings.height());
        box.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (width) settings.width(box.getSelected());
                else settings.height(box.getSelected());
                markDirty();
            }
        });

        content.add(label).width(112f);
        content.add(box).colspan(2).growX();
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
        seedField.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                markDirty();
            }
        });

        content.add(new Label("Seed", skin)).width(112f);
        content.add(seedField).growX();
        content.add(nextSeed).width(72f).padLeft(6f);
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

        content.add(new Label(name, skin)).width(112f);
        content.add(slider).width(178f).growX();
        content.add(value).width(58f).right().padLeft(8f);
        content.row();
    }

    private void addSection(Table content, String title) {
        Label label = new Label(title, skin, "subtitle");
        content.add(label).colspan(3).left().padTop(10f).padBottom(8f);
        content.row();
    }

    private void generateFromControls() {
        String seedText = seedField.getText().trim();
        try {
            settings.seed(Long.parseLong(seedText));
        } catch (NumberFormatException exception) {
            statusLabel.setText("Seed must be a signed 64-bit integer.");
            return;
        }

        generateAction.run();
        stage.setKeyboardFocus(null);
        statusLabel.setText("Generated from current settings.");
    }

    private void markDirty() {
        statusLabel.setText("Pending changes - press Generate.");
    }

    private static String formatPercent(int ppm) {
        return Math.round(ppm / 10_000f) + "%";
    }
}
