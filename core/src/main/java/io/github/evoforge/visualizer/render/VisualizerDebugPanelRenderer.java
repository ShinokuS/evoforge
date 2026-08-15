package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.interaction.VisualizerDebugPanel;

/** Compact always-available debug overlay panel with clickable checkboxes. */
public final class VisualizerDebugPanelRenderer {

    private static final String HEADER_TEXT = "DEBUG OVERLAYS  F1 hide";
    private static final float PAD_X = 14f;
    private static final float CHECK_SIZE = 16f;
    private static final float CHECK_GAP = 11f;
    private static final float ROW_PAD_Y = 5f;
    private static final float HEADER_PAD_Y = 6f;

    private static final Color PANEL = new Color(0.025f, 0.033f, 0.038f, 0.96f);
    private static final Color HEADER = new Color(0.055f, 0.075f, 0.068f, 1f);
    private static final Color BORDER = new Color(0.28f, 0.43f, 0.35f, 1f);
    private static final Color ROW_HOVER = new Color(0.11f, 0.16f, 0.14f, 0.95f);
    private static final Color TEXT = new Color(0.93f, 0.96f, 0.94f, 1f);
    private static final Color MUTED = new Color(0.69f, 0.74f, 0.71f, 1f);
    private static final Color CHECK = new Color(0.45f, 0.82f, 0.56f, 1f);
    private static final Color BOX = new Color(0.37f, 0.46f, 0.41f, 1f);

    private final VisualizerState state;
    private final VisualizerDebugPanel panel;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();
    private int height = 1;

    public VisualizerDebugPanelRenderer(
            VisualizerState state,
            VisualizerDebugPanel panel,
            VisualizerUiAssets ui) {
        if (state == null || panel == null || ui == null) {
            throw new IllegalArgumentException("debug panel dependencies must not be null");
        }
        this.state = state;
        this.panel = panel;
        this.font = ui.largeList();
        configureMeasuredLayout();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw() {
        if (!state.debugPanelVisible()) return;

        float x = panel.x();
        float top = height - panel.yTop();
        float bottom = top - panel.height();
        float panelWidth = panel.width();
        float headerHeight = panel.headerHeight();
        float rowHeight = panel.rowHeight();
        VisualizerDebugPanel.Option hovered = panel.optionAt(Gdx.input.getX(), Gdx.input.getY());

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, bottom, panelWidth, panel.height());
        shapes.setColor(HEADER);
        shapes.rect(x, top - headerHeight, panelWidth, headerHeight);

        for (int index = 0; index < VisualizerDebugPanel.optionCount(); index++) {
            VisualizerDebugPanel.Option option = VisualizerDebugPanel.option(index);
            float rowTop = top - headerHeight - index * rowHeight;
            if (option == hovered) {
                shapes.setColor(ROW_HOVER);
                shapes.rect(x + 1f, rowTop - rowHeight, panelWidth - 2f, rowHeight);
            }
            float boxX = x + PAD_X;
            float boxY = rowTop - (rowHeight + CHECK_SIZE) * 0.5f;
            boolean checked = option.checked(state);
            shapes.setColor(checked ? CHECK : BOX);
            shapes.rect(boxX, boxY, CHECK_SIZE, CHECK_SIZE);
            if (!checked) {
                shapes.setColor(PANEL);
                shapes.rect(boxX + 2f, boxY + 2f, CHECK_SIZE - 4f, CHECK_SIZE - 4f);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x, bottom, panelWidth, panel.height());
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.setColor(TEXT);
        font.draw(batch, HEADER_TEXT, x + PAD_X,
                top - (headerHeight - font.getLineHeight()) * 0.5f);
        for (int index = 0; index < VisualizerDebugPanel.optionCount(); index++) {
            VisualizerDebugPanel.Option option = VisualizerDebugPanel.option(index);
            float rowTop = top - headerHeight - index * rowHeight;
            font.setColor(option.checked(state) ? TEXT : MUTED);
            font.draw(batch, option.label(),
                    x + PAD_X + CHECK_SIZE + CHECK_GAP,
                    rowTop - (rowHeight - font.getLineHeight()) * 0.5f);
        }
        batch.end();
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
    }

    private void configureMeasuredLayout() {
        float widest = textWidth(HEADER_TEXT);
        for (int index = 0; index < VisualizerDebugPanel.optionCount(); index++) {
            widest = Math.max(
                    widest,
                    CHECK_SIZE + CHECK_GAP + textWidth(VisualizerDebugPanel.option(index).label()));
        }
        panel.configureLayout(
                widest + PAD_X * 2f,
                font.getLineHeight() + HEADER_PAD_Y * 2f,
                font.getLineHeight() + ROW_PAD_Y * 2f);
    }

    private float textWidth(String text) {
        layout.setText(font, text);
        return layout.width;
    }
}
