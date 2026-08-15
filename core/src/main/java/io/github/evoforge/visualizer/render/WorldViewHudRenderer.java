package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.visualizer.VisualizerState;

/** Content-sized perspective/command status; controls live in the debug panel. */
public final class WorldViewHudRenderer {

    private static final Color PANEL = new Color(0.030f, 0.040f, 0.046f, 0.96f);
    private static final Color BORDER = new Color(0.31f, 0.52f, 0.39f, 0.96f);
    private static final Color TEXT = new Color(0.94f, 0.985f, 0.93f, 1f);
    private static final Color MOVE = new Color(0.52f, 0.86f, 1.00f, 1f);
    private static final float MARGIN = 12f;
    private static final float GAP = 8f;
    private static final float PAD_X = 14f;
    private static final float PAD_Y = 10f;

    private final SimulationView view;
    private final VisualizerState state;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();
    private int width = 1;
    private int height = 1;

    public WorldViewHudRenderer(
            SimulationView view,
            VisualizerState state,
            VisualizerUiAssets ui) {
        if (view == null || state == null || ui == null) {
            throw new IllegalArgumentException("world-view HUD dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.font = ui.largeList();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw() {
        String text = label();
        layout.setText(font, text);
        float panelWidth = Math.min(
                layout.width + PAD_X * 2f,
                Math.max(1f, width - MARGIN * 2f));
        float panelHeight = font.getLineHeight() + PAD_Y * 2f;
        float runtimePanelHeight = font.getLineHeight() + PAD_Y * 2f;
        float x = MARGIN;
        float y = Math.max(
                MARGIN,
                height - MARGIN - runtimePanelHeight - GAP - panelHeight);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, y, panelWidth, panelHeight);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x, y, panelWidth, panelHeight);
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.setColor(state.moveTargeting() || selectedMoveActive() ? MOVE : TEXT);
        font.draw(batch, text, x + PAD_X, y + panelHeight - PAD_Y);
        batch.end();
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
    }

    private String label() {
        String mode = switch (state.viewMode()) {
            case SURFACE -> "SURFACE";
            case INTERIOR -> "INTERIOR  "
                    + (state.interior() == null ? "Interior" : state.interior().label())
                    + "  Z " + state.selectedZ();
            case DEBUG_SLICE -> "SLICE VIEW  Z " + state.selectedZ();
        };
        if (state.moveTargeting()) return mode + "   MOVE: choose destination   Esc cancel";
        if (selectedMoveActive()) return mode + "   MOVING";
        String message = state.interactionMessage();
        return message.isBlank() ? mode : mode + "   " + message;
    }

    private boolean selectedMoveActive() {
        ObjectId selected = state.selectedObject();
        return selected != null && view.moveTo().isActive(selected);
    }
}
