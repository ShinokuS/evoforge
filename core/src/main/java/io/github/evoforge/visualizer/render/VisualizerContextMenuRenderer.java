package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import io.github.evoforge.visualizer.interaction.VisualizerContextMenu;

/** Small immediate-mode context menu; no Scene2D tree or persistent widget graph. */
public final class VisualizerContextMenuRenderer {

    private static final float PAD_X = 14f;
    private static final float HEADER_PAD_Y = 6f;
    private static final float ROW_PAD_Y = 5f;

    private static final Color PANEL = new Color(0.030f, 0.040f, 0.046f, 0.985f);
    private static final Color BORDER = new Color(0.38f, 0.58f, 0.44f, 1f);
    private static final Color HEADER = new Color(0.065f, 0.095f, 0.080f, 1f);
    private static final Color HOVER = new Color(0.17f, 0.29f, 0.22f, 0.98f);
    private static final Color TEXT = new Color(0.97f, 0.985f, 0.97f, 1f);
    private static final Color MUTED = new Color(0.78f, 0.83f, 0.79f, 1f);

    private final VisualizerContextMenu menu;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();
    private int width = 1;
    private int height = 1;

    public VisualizerContextMenuRenderer(VisualizerContextMenu menu, VisualizerUiAssets ui) {
        if (menu == null || ui == null) {
            throw new IllegalArgumentException("context menu dependencies must not be null");
        }
        this.menu = menu;
        this.font = ui.largeList();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw() {
        if (!menu.visible()) return;
        configureMeasuredLayout();

        float x = menu.x();
        float top = height - menu.yTop();
        float menuHeight = menu.height();
        float menuWidth = menu.width();
        float headerHeight = menu.headerHeight();
        float rowHeight = menu.rowHeight();
        float bottom = top - menuHeight;
        VisualizerContextMenu.Action hovered = menu.actionAt(
                Gdx.input.getX(),
                Gdx.input.getY());

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, bottom, menuWidth, menuHeight);
        shapes.setColor(HEADER);
        shapes.rect(x, top - headerHeight, menuWidth, headerHeight);

        for (int index = 0; index < menu.actions().size(); index++) {
            VisualizerContextMenu.Action action = menu.actions().get(index);
            if (action != hovered) continue;
            float rowTop = top - headerHeight - index * rowHeight;
            shapes.setColor(HOVER);
            shapes.rect(x + 1f, rowTop - rowHeight, menuWidth - 2f, rowHeight);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x, bottom, menuWidth, menuHeight);
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.setColor(TEXT);
        font.draw(batch, menu.title(), x + PAD_X,
                top - (headerHeight - font.getLineHeight()) * 0.5f,
                menuWidth - PAD_X * 2f, Align.left, false);

        for (int index = 0; index < menu.actions().size(); index++) {
            VisualizerContextMenu.Action action = menu.actions().get(index);
            float rowTop = top - headerHeight - index * rowHeight;
            font.setColor(action == hovered ? TEXT : MUTED);
            font.draw(batch, action.label(), x + PAD_X,
                    rowTop - (rowHeight - font.getLineHeight()) * 0.5f,
                    menuWidth - PAD_X * 2f, Align.left, false);
        }
        batch.end();
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
    }

    private void configureMeasuredLayout() {
        float widest = textWidth(menu.title());
        for (VisualizerContextMenu.Action action : menu.actions()) {
            widest = Math.max(widest, textWidth(action.label()));
        }
        float measuredWidth = widest + PAD_X * 2f;
        float availableWidth = Math.max(1f, width - 16f);
        menu.configureLayout(
                Math.min(measuredWidth, availableWidth),
                font.getLineHeight() + HEADER_PAD_Y * 2f,
                font.getLineHeight() + ROW_PAD_Y * 2f);
    }

    private float textWidth(String text) {
        layout.setText(font, text);
        return layout.width;
    }
}
