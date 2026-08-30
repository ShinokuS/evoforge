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
import com.badlogic.gdx.utils.Align;
import io.github.evoforge.visualizer.scenario.ScenarioCatalog;
import io.github.evoforge.visualizer.scenario.VisualizerScenario;
import java.util.function.Consumer;

/** Searchable grouped browser for deterministic development scenarios. */
public final class ScenarioMenuScreen extends ScreenAdapter {

    private static final Color BACKGROUND = new Color(0.035f, 0.045f, 0.052f, 1f);
    private static final Color PANEL = new Color(0.065f, 0.078f, 0.088f, 1f);
    private static final Color PANEL_ALT = new Color(0.085f, 0.10f, 0.11f, 1f);
    private static final Color SELECTED = new Color(0.12f, 0.28f, 0.32f, 1f);
    private static final Color ACCENT = new Color(0.38f, 0.90f, 0.94f, 1f);
    private static final Color MUTED = new Color(0.66f, 0.70f, 0.72f, 1f);

    private static final float MARGIN = 42f;
    private static final float HEADER_HEIGHT = 126f;
    private static final float ROW_HEIGHT = 44f;
    private static final float LIST_BOTTOM = 42f;
    private static final float PANEL_GAP = 30f;

    private final ScenarioCatalog catalog;
    private final ScenarioMenuModel model;
    private final Consumer<VisualizerScenario> openScenario;
    private final Runnable returnToWorkspace;
    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new MenuInput();

    private int width = 1;
    private int height = 1;
    private int scrollOffset;

    public ScenarioMenuScreen(
            ScenarioCatalog catalog,
            Consumer<VisualizerScenario> openScenario,
            Runnable returnToWorkspace) {
        if (catalog == null) throw new IllegalArgumentException("catalog must not be null");
        if (openScenario == null) throw new IllegalArgumentException("openScenario must not be null");
        if (returnToWorkspace == null) {
            throw new IllegalArgumentException("returnToWorkspace must not be null");
        }
        this.catalog = catalog;
        this.model = new ScenarioMenuModel(catalog);
        this.openScenario = openScenario;
        this.returnToWorkspace = returnToWorkspace;
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
        ensureSelectionVisible();
        drawPanels();
        drawText();
    }

    @Override
    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
        clampScroll();
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

    private void drawPanels() {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float listWidth = listWidth();
        float listTop = listTop();
        shapes.setColor(PANEL);
        shapes.rect(MARGIN, LIST_BOTTOM, listWidth, listTop - LIST_BOTTOM);

        float detailX = detailX();
        float detailWidth = Math.max(0f, width - detailX - MARGIN);
        if (detailWidth > 180f) {
            shapes.setColor(PANEL);
            shapes.rect(detailX, LIST_BOTTOM, detailWidth, listTop - LIST_BOTTOM);
        }

        int capacity = visibleCapacity();
        int end = Math.min(model.rowCount(), scrollOffset + capacity);
        for (int index = scrollOffset; index < end; index++) {
            float bottom = rowBottom(index - scrollOffset);
            ScenarioMenuModel.Row row = model.row(index);
            if (row.isGroup()) {
                shapes.setColor(PANEL_ALT);
                shapes.rect(MARGIN, bottom, listWidth, ROW_HEIGHT);
            }
            if (index == model.selectedIndex()) {
                shapes.setColor(SELECTED);
                shapes.rect(MARGIN + 3f, bottom + 2f, listWidth - 6f, ROW_HEIGHT - 4f);
            }
        }
        shapes.end();
    }

    private void drawText() {
        batch.setProjectionMatrix(projection);
        batch.begin();

        font.getData().setScale(1.18f);
        font.setColor(Color.WHITE);
        font.draw(batch, "EVOFORGE / SCENARIOS", MARGIN, height - 38f);

        font.getData().setScale(0.88f);
        font.setColor(MUTED);
        font.draw(batch,
                catalog.size() + " scenarios / " + catalog.groups().size() + " groups",
                MARGIN,
                height - 74f);

        font.getData().setScale(0.92f);
        font.setColor(ACCENT);
        String search = model.query().isEmpty()
                ? "Search: type to filter scenarios..."
                : "Search: " + model.query();
        font.draw(batch, search, MARGIN, height - 108f);

        drawRows();
        drawDetails();
        batch.end();
    }

    private void drawRows() {
        int capacity = visibleCapacity();
        int end = Math.min(model.rowCount(), scrollOffset + capacity);
        float x = MARGIN + 18f;

        for (int index = scrollOffset; index < end; index++) {
            ScenarioMenuModel.Row row = model.row(index);
            float baseline = rowBottom(index - scrollOffset) + 30f;

            if (row.isGroup()) {
                font.getData().setScale(0.98f);
                font.setColor(index == model.selectedIndex() ? ACCENT : Color.WHITE);
                String arrow = model.isExpanded(row.group()) ? "v  " : ">  ";
                font.draw(batch,
                        arrow + row.group().title() + "  [" + row.group().scenarios().size() + "]",
                        x,
                        baseline);
            } else {
                font.getData().setScale(0.92f);
                font.setColor(index == model.selectedIndex()
                        ? Color.WHITE
                        : new Color(0.88f, 0.90f, 0.91f, 1f));
                font.draw(batch, "    " + row.scenario().title(), x + 12f, baseline);
            }
        }

        if (model.rowCount() == 0) {
            font.getData().setScale(0.92f);
            font.setColor(MUTED);
            font.draw(batch, "No scenarios match this search.", x, listTop() - 34f);
        }
    }

    private void drawDetails() {
        float x = detailX() + 22f;
        float available = width - x - MARGIN - 20f;
        if (available <= 160f) return;

        ScenarioMenuModel.Row row = model.rowCount() == 0 ? null : model.selectedRow();
        float y = listTop() - 28f;

        if (row == null) {
            font.getData().setScale(0.92f);
            font.setColor(MUTED);
            font.draw(batch, "No selection", x, y);
            return;
        }

        if (row.isGroup()) {
            font.getData().setScale(1.02f);
            font.setColor(ACCENT);
            font.draw(batch, row.group().title(), x, y);

            font.getData().setScale(0.90f);
            font.setColor(Color.WHITE);
            font.draw(batch,
                    row.group().scenarios().size() + " focused development scenarios",
                    x,
                    y - 42f);

            font.setColor(MUTED);
            font.draw(batch,
                    "Enter/click toggles this group. Type anywhere to search across all groups.",
                    x,
                    y - 88f,
                    available,
                    Align.left,
                    true);
        } else {
            font.getData().setScale(0.82f);
            font.setColor(MUTED);
            font.draw(batch, row.group().title(), x, y);

            font.getData().setScale(1.04f);
            font.setColor(Color.WHITE);
            font.draw(batch, row.scenario().title(), x, y - 38f);

            font.getData().setScale(0.90f);
            font.setColor(new Color(0.88f, 0.90f, 0.91f, 1f));
            font.draw(batch,
                    row.scenario().description(),
                    x,
                    y - 86f,
                    available,
                    Align.left,
                    true);

            font.setColor(MUTED);
            font.draw(batch, "id: " + row.scenario().id(), x, LIST_BOTTOM + 86f);
        }

        font.getData().setScale(0.78f);
        font.setColor(MUTED);
        font.draw(batch,
                "Up/Down select | Left/Right collapse/expand | Enter open | mouse wheel scroll | Backspace edit search | Esc clear/back",
                x,
                LIST_BOTTOM + 34f,
                available,
                Align.left,
                true);
    }

    private void activateSelected() {
        if (model.rowCount() == 0) return;
        VisualizerScenario scenario = model.activateSelected();
        clampScroll();
        ensureSelectionVisible();
        if (scenario != null) openScenario.accept(scenario);
    }

    private void selectRow(int index) {
        model.select(index);
        ensureSelectionVisible();
    }

    private void scrollBy(int rows) {
        scrollOffset += rows;
        clampScroll();
    }

    private void ensureSelectionVisible() {
        if (model.rowCount() == 0) {
            scrollOffset = 0;
            return;
        }
        int capacity = visibleCapacity();
        int selected = model.selectedIndex();
        if (selected < scrollOffset) {
            scrollOffset = selected;
        } else if (selected >= scrollOffset + capacity) {
            scrollOffset = selected - capacity + 1;
        }
        clampScroll();
    }

    private void clampScroll() {
        int max = Math.max(0, model.rowCount() - visibleCapacity());
        scrollOffset = Math.max(0, Math.min(scrollOffset, max));
    }

    private int visibleCapacity() {
        return Math.max(1, (int) ((listTop() - LIST_BOTTOM) / ROW_HEIGHT));
    }

    private float rowBottom(int visibleIndex) {
        return listTop() - (visibleIndex + 1) * ROW_HEIGHT;
    }

    private float listTop() {
        return height - HEADER_HEIGHT;
    }

    private float listWidth() {
        return Math.max(330f, Math.min(610f, width * 0.48f));
    }

    private float detailX() {
        return MARGIN + listWidth() + PANEL_GAP;
    }

    private int rowAt(int screenY) {
        float y = height - screenY;
        float listTop = listTop();
        if (y < LIST_BOTTOM || y > listTop) return -1;
        int visibleIndex = (int) ((listTop - y) / ROW_HEIGHT);
        int index = scrollOffset + visibleIndex;
        return index >= 0 && index < model.rowCount() ? index : -1;
    }

    private final class MenuInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.UP -> {
                    model.moveSelection(-1);
                    ensureSelectionVisible();
                    return true;
                }
                case Input.Keys.DOWN -> {
                    model.moveSelection(1);
                    ensureSelectionVisible();
                    return true;
                }
                case Input.Keys.LEFT -> {
                    if (model.rowCount() > 0) {
                        model.collapseSelectedOrParent();
                        ensureSelectionVisible();
                    }
                    return true;
                }
                case Input.Keys.RIGHT -> {
                    if (model.rowCount() > 0) {
                        model.expandSelected();
                        ensureSelectionVisible();
                    }
                    return true;
                }
                case Input.Keys.ENTER -> {
                    activateSelected();
                    return true;
                }
                case Input.Keys.BACKSPACE -> {
                    model.backspaceQuery();
                    scrollOffset = 0;
                    ensureSelectionVisible();
                    return true;
                }
                case Input.Keys.ESCAPE -> {
                    if (!model.query().isEmpty()) {
                        model.clearQuery();
                        scrollOffset = 0;
                        ensureSelectionVisible();
                    } else {
                        returnToWorkspace.run();
                    }
                    return true;
                }
                default -> {
                    return false;
                }
            }
        }

        @Override
        public boolean keyTyped(char character) {
            if (character < 32 || character == 127) return false;
            model.appendQuery(character);
            scrollOffset = 0;
            ensureSelectionVisible();
            return true;
        }

        @Override
        public boolean touchDown(int screenX, int screenY, int pointer, int button) {
            if (button != Input.Buttons.LEFT
                    || screenX < MARGIN
                    || screenX > MARGIN + listWidth()) {
                return false;
            }

            int index = rowAt(screenY);
            if (index < 0) return false;

            selectRow(index);
            ScenarioMenuModel.Row row = model.row(index);
            if (row.isGroup()) {
                model.toggle(row.group());
                clampScroll();
                ensureSelectionVisible();
            } else {
                openScenario.accept(row.scenario());
            }
            return true;
        }

        @Override
        public boolean scrolled(float amountX, float amountY) {
            int direction = amountY > 0f ? 1 : amountY < 0f ? -1 : 0;
            if (direction != 0) scrollBy(direction * 3);
            return direction != 0;
        }
    }
}
