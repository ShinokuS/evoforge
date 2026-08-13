package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerTimeController;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;

/** Screen-space status and inspector presentation for the visualizer. */
public final class VisualizerHudRenderer {

    private static final int INSPECT_EXPOSURE_DISTANCE = 12;

    private static final Color PANEL =
            new Color(0.035f, 0.045f, 0.052f, 0.96f);
    private static final Color PANEL_BORDER =
            new Color(0.24f, 0.30f, 0.31f, 1f);

    private final SimulationView view;
    private final SimulationTime simulationTime;
    private final VisualizerTimeController time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final LandscapeSliceResolver sliceResolver;
    private final ShapePresentationRegistry shapePresentations;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    private int width = 1;
    private int height = 1;

    public VisualizerHudRenderer(
            SimulationView view,
            SimulationTime simulationTime,
            VisualizerTimeController time,
            VisualizerState state,
            VisualizerCamera camera,
            LandscapeSliceResolver sliceResolver,
            ShapePresentationRegistry shapePresentations) {

        this.view = require(view, "view");
        this.simulationTime = require(simulationTime, "simulationTime");
        this.time = require(time, "time");
        this.state = require(state, "state");
        this.camera = require(camera, "camera");
        this.sliceResolver = require(sliceResolver, "sliceResolver");
        this.shapePresentations = require(
                shapePresentations,
                "shapePresentations");
    }

    public void resize(
            int width,
            int height) {

        if (width <= 0 || height <= 0) {
            return;
        }

        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw() {
        float margin = 12f;
        float statusWidth = Math.min(720f, width - margin * 2f);
        float statusHeight = 104f;
        float statusX = margin;
        float statusY = height - margin - statusHeight;

        VisualizerState.CellSelection selectedCell = state.selectedCell();
        ObjectId selectedObject = state.selectedObject();
        boolean hasInspector = selectedCell != null;
        float inspectorWidth = Math.min(380f, width - margin * 2f);
        float inspectorHeight = selectedObject == null ? 172f : 238f;
        float inspectorX = width - margin - inspectorWidth;
        float inspectorY = height - margin - inspectorHeight;

        if (hasInspector && inspectorX < statusX + statusWidth + margin) {
            inspectorX = margin;
            inspectorY = statusY - margin - inspectorHeight;
        }

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanel(statusX, statusY, statusWidth, statusHeight);
        if (hasInspector) {
            drawPanel(
                    inspectorX,
                    inspectorY,
                    inspectorWidth,
                    inspectorHeight);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_BORDER);
        shapes.rect(statusX, statusY, statusWidth, statusHeight);
        if (hasInspector) {
            shapes.rect(
                    inspectorX,
                    inspectorY,
                    inspectorWidth,
                    inspectorHeight);
        }
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.setColor(Color.WHITE);

        float textX = statusX + 12f;
        float top = statusY + statusHeight - 12f;
        font.draw(
                batch,
                "STATUS   tick " + simulationTime.tick()
                        + "   Z " + state.selectedZ()
                        + "   FPS " + Gdx.graphics.getFramesPerSecond()
                        + "   " + (time.running() ? "RUN x1" : "PAUSED"),
                textX,
                top);
        font.draw(
                batch,
                "Space run/pause | N step | PgUp/PgDn horizontal slice",
                textX,
                top - 22f);
        font.draw(
                batch,
                "WASD pan | wheel zoom " + camera.zoomLabel()
                        + " " + samplingLabel()
                        + " | G grid " + gridLabel(),
                textX,
                top - 44f);
        font.draw(
                batch,
                "F2 transitions " + onOff(state.showTransitions())
                        + " | F3 ramps " + onOff(state.showShapeDirections())
                        + " | F4 lower depth " + state.lowerDepth()
                        + " | F5 occupancy " + onOff(state.showOccupancy()),
                textX,
                top - 66f);

        if (hasInspector) {
            drawInspectorText(
                    selectedCell,
                    selectedObject,
                    inspectorX + 12f,
                    inspectorY + inspectorHeight - 12f);
        }

        batch.end();
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    private void drawPanel(
            float x,
            float y,
            float width,
            float height) {

        shapes.setColor(PANEL);
        shapes.rect(x, y, width, height);
    }

    private void drawInspectorText(
            VisualizerState.CellSelection selectedCell,
            ObjectId selectedObject,
            float x,
            float top) {

        LandscapeSliceResolver.Cell slice = sliceResolver.analyze(
                selectedCell.x(),
                selectedCell.x(),
                selectedCell.y(),
                selectedCell.y(),
                selectedCell.z(),
                state.lowerDepth(),
                INSPECT_EXPOSURE_DISTANCE)
                .resolve(selectedCell.x(), selectedCell.y());
        int transitions = view.navigation().transitions(
                selectedCell.x(),
                selectedCell.y(),
                selectedCell.z());

        font.draw(
                batch,
                "CELL   (" + selectedCell.x()
                        + ", " + selectedCell.y()
                        + ", " + selectedCell.z() + ")",
                x,
                top);
        font.draw(batch, "slice: " + sliceLabel(slice), x, top - 22f);
        font.draw(batch, "context: " + contextLabel(slice), x, top - 44f);
        font.draw(
                batch,
                "shape: " + shapePresentations.debugLabel(slice.shape()),
                x,
                top - 66f);
        font.draw(
                batch,
                "transitions: " + Integer.bitCount(transitions),
                x,
                top - 88f);
        font.draw(
                batch,
                "occupancy: " + view.occupancy().state(
                        selectedCell.x(),
                        selectedCell.y(),
                        selectedCell.z()),
                x,
                top - 110f);

        if (selectedObject == null) {
            return;
        }

        WorldObject object = view.objects().get(selectedObject);
        if (object == null || !view.transforms().has(selectedObject)) {
            return;
        }

        font.draw(batch, "OBJECT   " + selectedObject, x, top - 140f);
        font.draw(
                batch,
                "definition: " + object.definitionId(),
                x,
                top - 162f);
        font.draw(
                batch,
                "XYZ: "
                        + view.transforms().x(selectedObject)
                        + ", "
                        + view.transforms().y(selectedObject)
                        + ", "
                        + view.transforms().z(selectedObject),
                x,
                top - 184f);
    }

    private static String sliceLabel(
            LandscapeSliceResolver.Cell cell) {

        return switch (cell.kind()) {
            case SOLID_BODY -> "SOLID BODY terrain Z=" + cell.terrainZ();
            case CURRENT_SURFACE -> "SURFACE terrain Z=" + cell.terrainZ();
            case LOWER_SURFACE -> "LOWER depth " + cell.dropDepth()
                    + " terrain Z=" + cell.terrainZ();
            case EMPTY -> "EMPTY";
        };
    }

    private static String contextLabel(
            LandscapeSliceResolver.Cell cell) {

        if (cell.kind() == LandscapeSliceResolver.Kind.EMPTY) {
            return "none";
        }
        if (cell.kind() == LandscapeSliceResolver.Kind.SOLID_BODY) {
            return "body depth " + cell.bodyDepth();
        }
        if (!cell.covered()) {
            return "open sky | exposure 0";
        }
        return "ceiling " + cell.ceilingDistance()
                + " | cover " + cell.coverDepth()
                + " | exposure " + cell.exposureDistance();
    }

    private String gridLabel() {
        return switch (state.gridMode()) {
            case 0 -> "OFF";
            case 1 -> "SUBTLE";
            default -> "DEBUG";
        };
    }

    private String samplingLabel() {
        return camera.smoothLandscapeSampling() ? "LINEAR" : "NEAREST";
    }

    private static String onOff(
            boolean value) {

        return value ? "ON" : "OFF";
    }

    private static <T> T require(
            T value,
            String name) {

        if (value == null) {
            throw new IllegalArgumentException(name + " must not be null");
        }
        return value;
    }
}
