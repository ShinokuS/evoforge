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
import io.github.evoforge.visualizer.continuum.ContinuumInfiniteTimeInspectorModel;

/** Plain visual proof that world age does not imply tick replay or retained history. */
public final class ContinuumInfiniteTimeInspectorScreen extends ScreenAdapter {

    private static final Color BACKGROUND = new Color(0.035f, 0.045f, 0.052f, 1f);
    private static final Color PANEL = new Color(0.065f, 0.078f, 0.088f, 1f);
    private static final Color SLEEPING = new Color(0.24f, 0.58f, 0.92f, 1f);
    private static final Color WOKEN = new Color(0.42f, 0.82f, 0.44f, 1f);
    private static final Color ACCENT = new Color(1.00f, 0.82f, 0.28f, 1f);
    private static final Color MUTED = new Color(0.70f, 0.74f, 0.76f, 1f);

    private final Runnable returnToMenu;
    private final ContinuumInfiniteTimeInspectorModel model = new ContinuumInfiniteTimeInspectorModel();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final Skin skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
    private final BitmapFont font = skin.getFont("window");
    private final Matrix4 projection = new Matrix4();
    private final InputAdapter input = new InspectorInput();

    private int width = 1;
    private int height = 1;

    public ContinuumInfiniteTimeInspectorScreen(Runnable returnToMenu) {
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
        drawPanelsAndProcesses();
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
        shapes.dispose();
        batch.dispose();
        skin.dispose();
    }

    private void drawPanelsAndProcesses() {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);

        float left = 28f;
        float top = height - 150f;
        float panelWidth = Math.max(260f, (width - 84f) / 3f);
        float panelHeight = Math.max(250f, height - 230f);

        shapes.setColor(PANEL);
        shapes.rect(left, top - panelHeight, panelWidth, panelHeight);
        shapes.rect(left + panelWidth + 14f, top - panelHeight, panelWidth, panelHeight);
        shapes.rect(left + (panelWidth + 14f) * 2f, top - panelHeight, panelWidth, panelHeight);

        float processX = left + panelWidth + 38f;
        float processY = top - 110f;
        float processWidth = panelWidth - 48f;
        for (var row : model.processRows()) {
            shapes.setColor(row.sleeping() ? SLEEPING : WOKEN);
            shapes.rect(processX, processY, processWidth, 28f);
            processY -= 42f;
        }
        shapes.end();
    }

    private void drawText() {
        batch.setProjectionMatrix(projection);
        batch.begin();

        float x = 28f;
        float y = height - 28f;
        font.getData().setScale(1.05f);
        font.setColor(Color.WHITE);
        font.draw(batch, "STAGE 2 / WORLD AGE DOES NOT CREATE WORK", x, y);

        y -= 32f;
        font.getData().setScale(0.80f);
        font.setColor(MUTED);
        font.draw(batch,
                "The world may be extremely old. Cost follows CURRENT work, not all past ticks.",
                x,
                y);

        float top = height - 150f;
        float panelWidth = Math.max(260f, (width - 84f) / 3f);
        drawAgePanel(28f + 20f, top - 24f, panelWidth - 40f);
        drawSleepPanel(28f + panelWidth + 34f, top - 24f, panelWidth - 40f);
        drawHistoryPanel(28f + (panelWidth + 14f) * 2f + 20f, top - 24f, panelWidth - 40f);

        font.getData().setScale(0.78f);
        font.setColor(Color.WHITE);
        font.draw(batch,
                "1 young world   2 ancient world   3 jump 1,000,000,000,000,000 ticks   4 compact 1,000,000 changes   Esc back",
                28f,
                28f);
        batch.end();
    }

    private void drawAgePanel(float x, float y, float width) {
        font.getData().setScale(0.95f);
        font.setColor(ACCENT);
        font.draw(batch, "A. WORLD AGE", x, y);

        font.getData().setScale(0.82f);
        font.setColor(Color.WHITE);
        y -= 38f;
        font.draw(batch, "Era: " + model.now().era(), x, y);
        y -= 26f;
        font.draw(batch, "Tick inside era: " + model.now().tickWithinEra(), x, y);
        y -= 42f;
        font.setColor(MUTED);
        font.draw(batch, "Age is stored as two exact integers.", x, y, width, Align.left, true);
        y -= 54f;
        font.draw(batch, "Press 1 and 2: age changes enormously, but the same six sleeping processes still need only six wake records.", x, y, width, Align.left, true);
    }

    private void drawSleepPanel(float x, float y, float width) {
        font.getData().setScale(0.95f);
        font.setColor(ACCENT);
        font.draw(batch, "B. SLEEPING WORK", x, y);

        font.getData().setScale(0.80f);
        font.setColor(Color.WHITE);
        y -= 38f;
        font.draw(batch, "Sleeping now: " + model.sleepingProcesses(), x, y);
        y -= 24f;
        font.draw(batch, "Wake records stored: " + model.queuedWakeEntries(), x, y);
        y -= 24f;
        font.draw(batch, "Last huge jump: " + model.lastJumpTicks() + " ticks", x, y);
        y -= 24f;
        font.draw(batch, "Processes actually handled: " + model.lastWakeOperations(), x, y);
        y -= 34f;
        font.setColor(MUTED);
        font.draw(batch, "Blue = sleeping. Green = woken. A huge time jump handles due processes once; it does not replay every missing tick.", x, y, width, Align.left, true);
    }

    private void drawHistoryPanel(float x, float y, float width) {
        font.getData().setScale(0.95f);
        font.setColor(ACCENT);
        font.draw(batch, "C. HISTORY KEPT", x, y);

        font.getData().setScale(0.80f);
        font.setColor(Color.WHITE);
        y -= 38f;
        font.draw(batch, "Current compacted value: " + model.compactedCurrentState(), x, y);
        y -= 24f;
        font.draw(batch, "Recent changes retained: " + model.retainedHistoryEntries()
                + " / max " + ContinuumInfiniteTimeInspectorModel.HISTORY_TAIL_LIMIT, x, y);
        y -= 24f;
        font.draw(batch, "Compactions performed: " + model.compactions(), x, y);
        y -= 34f;
        font.setColor(MUTED);
        font.draw(batch, "Press 4. One million historical changes become the current state plus a bounded recent tail, not one million permanent records.", x, y, width, Align.left, true);
        y -= 82f;
        font.setColor(Color.WHITE);
        font.draw(batch, "10,000 cancelled scheduler tasks -> queue entries: " + model.schedulerChurnQueueEntries(), x, y, width, Align.left, true);
        y -= 42f;
        font.draw(batch, "Reusable handle slots kept: " + model.schedulerChurnHandleSlots(), x, y, width, Align.left, true);
    }

    private final class InspectorInput extends InputAdapter {
        @Override
        public boolean keyDown(int keycode) {
            switch (keycode) {
                case Input.Keys.NUM_1, Input.Keys.NUMPAD_1 -> model.setYoungWorld();
                case Input.Keys.NUM_2, Input.Keys.NUMPAD_2 -> model.setAncientWorld();
                case Input.Keys.NUM_3, Input.Keys.NUMPAD_3 -> model.jumpHugeInterval();
                case Input.Keys.NUM_4, Input.Keys.NUMPAD_4 -> model.compactMillionChanges();
                case Input.Keys.ESCAPE -> returnToMenu.run();
                default -> { return false; }
            }
            return true;
        }
    }
}
