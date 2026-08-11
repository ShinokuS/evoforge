package io.github.evoforge.visualizer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import io.github.evoforge.simulation.runtime.SimulationRuntime;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;

/**
 * Minimal debug presentation of one authoritative navigation Z plane plus the
 * immediately lower plane. No presentation state is written back into the
 * simulation.
 */
public final class ZLevelVisualizer extends InputAdapter {

    private static final float BASE_VIEW_WIDTH = 20f;
    private static final float PAN_SPEED = 8f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 4f;

    private static final Color BACKGROUND =
            new Color(0.055f, 0.065f, 0.085f, 1f);
    private static final Color FULL =
            new Color(0.38f, 0.43f, 0.49f, 1f);
    private static final Color RAMP =
            new Color(0.63f, 0.48f, 0.27f, 1f);
    private static final Color OTHER_SHAPE =
            new Color(0.48f, 0.38f, 0.58f, 1f);
    private static final Color GRID =
            new Color(0.15f, 0.17f, 0.21f, 1f);
    private static final Color RAMP_ARROW =
            new Color(0.95f, 0.84f, 0.35f, 1f);
    private static final Color OBJECT_EVEN =
            new Color(0.28f, 0.76f, 0.92f, 1f);
    private static final Color OBJECT_ODD =
            new Color(0.85f, 0.43f, 0.54f, 1f);
    private static final Color SELECTED =
            new Color(1f, 0.93f, 0.35f, 1f);
    private static final Color TRANSITION_FLAT =
            new Color(0.32f, 0.86f, 0.73f, 1f);
    private static final Color TRANSITION_UP =
            new Color(0.46f, 0.86f, 0.42f, 1f);
    private static final Color TRANSITION_DOWN =
            new Color(0.96f, 0.55f, 0.28f, 1f);

    private final SimulationRuntime runtime;
    private final SimulationView view;
    private final VisualizerTimeController time;

    private final OrthographicCamera camera =
            new OrthographicCamera();
    private final ShapeRenderer shapes =
            new ShapeRenderer();
    private final SpriteBatch batch =
            new SpriteBatch();
    private final BitmapFont font =
            new BitmapFont();
    private final Vector3 pick =
            new Vector3();

    private int selectedZ;
    private CellSelection selectedCell;
    private ObjectId selectedObject;

    public ZLevelVisualizer(
            SimulationRuntime runtime) {

        if (runtime == null) {
            throw new IllegalArgumentException(
                    "runtime must not be null");
        }

        this.runtime = runtime;
        view = runtime.view();
        time = new VisualizerTimeController(
                runtime.stepper(),
                0.25f);

        camera.position.set(0f, 0f, 0f);
        resize(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());

        Gdx.input.setInputProcessor(this);
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        updateCamera(delta);
        time.update(delta);

        Gdx.gl.glClearColor(
                BACKGROUND.r,
                BACKGROUND.g,
                BACKGROUND.b,
                BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        VisibleRange range = visibleRange();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawTerrainPlane(
                range,
                selectedZ - 1,
                true);
        drawTerrainPlane(
                range,
                selectedZ,
                false);
        drawObjects(range);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawGrid(range);
        drawRampDirections(
                range,
                selectedZ - 1,
                true);
        drawRampDirections(
                range,
                selectedZ,
                false);
        drawCellSelection();
        drawTransitionOverlay();
        drawSelectedObject();
        shapes.end();

        drawHud();
    }

    public void resize(
            int width,
            int height) {

        if (width <= 0 || height <= 0) {
            return;
        }

        camera.viewportWidth = BASE_VIEW_WIDTH;
        camera.viewportHeight = BASE_VIEW_WIDTH
                * (float) height
                / (float) width;
        camera.update();

        batch.getProjectionMatrix().setToOrtho2D(
                0f,
                0f,
                width,
                height);
    }

    public void dispose() {
        if (Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    @Override
    public boolean keyDown(
            int keycode) {

        switch (keycode) {
            case Input.Keys.SPACE -> {
                time.toggleRunning();
                return true;
            }
            case Input.Keys.N -> {
                if (!time.running()) {
                    time.stepOnce();
                }
                return true;
            }
            case Input.Keys.PAGE_UP -> {
                selectedZ++;
                clearSelection();
                return true;
            }
            case Input.Keys.PAGE_DOWN -> {
                selectedZ--;
                clearSelection();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean scrolled(
            float amountX,
            float amountY) {

        camera.zoom = MathUtils.clamp(
                camera.zoom * (1f + amountY * 0.1f),
                MIN_ZOOM,
                MAX_ZOOM);
        return true;
    }

    @Override
    public boolean touchDown(
            int screenX,
            int screenY,
            int pointer,
            int button) {

        if (button != Input.Buttons.LEFT) {
            return false;
        }

        pick.set(
                screenX,
                screenY,
                0f);
        camera.unproject(pick);

        int x = MathUtils.floor(pick.x);
        int y = MathUtils.floor(pick.y);

        selectedCell = new CellSelection(
                x,
                y,
                selectedZ);

        int count = view.cells().objectCount(
                x,
                y,
                selectedZ);

        selectedObject = count == 0
                ? null
                : view.cells().objectAt(
                        x,
                        y,
                        selectedZ,
                        0);

        return true;
    }

    private void updateCamera(
            float delta) {

        float distance = PAN_SPEED
                * camera.zoom
                * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            camera.position.x -= distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            camera.position.x += distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            camera.position.y -= distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            camera.position.y += distance;
        }
    }

    private VisibleRange visibleRange() {
        float halfWidth =
                camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight =
                camera.viewportHeight * camera.zoom * 0.5f;

        return new VisibleRange(
                MathUtils.floor(camera.position.x - halfWidth) - 1,
                MathUtils.ceil(camera.position.x + halfWidth) + 1,
                MathUtils.floor(camera.position.y - halfHeight) - 1,
                MathUtils.ceil(camera.position.y + halfHeight) + 1);
    }

    private void drawTerrainPlane(
            VisibleRange range,
            int standingZ,
            boolean dimmed) {

        int terrainZ = standingZ - 1;

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                if (!view.terrain().contains(
                        x,
                        y,
                        terrainZ)) {
                    continue;
                }

                Shape shape = view.geometry().find(
                        x,
                        y,
                        terrainZ);

                Color base = shape instanceof RampShape
                        ? RAMP
                        : shape == null
                                ? OTHER_SHAPE
                                : FULL;

                setLayerColor(
                        base,
                        dimmed);
                shapes.rect(
                        x + 0.04f,
                        y + 0.04f,
                        0.92f,
                        0.92f);
            }
        }
    }

    private void drawObjects(
            VisibleRange range) {

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                int count = view.cells().objectCount(
                        x,
                        y,
                        selectedZ);

                for (int index = 0; index < count; index++) {
                    ObjectId id = view.cells().objectAt(
                            x,
                            y,
                            selectedZ,
                            index);
                    WorldObject object = view.objects().get(id);

                    if (object == null) {
                        continue;
                    }

                    shapes.setColor(
                            object.definitionId().asInt() % 2 == 0
                                    ? OBJECT_EVEN
                                    : OBJECT_ODD);

                    float offset = Math.min(index, 3) * 0.12f;
                    shapes.circle(
                            x + 0.5f + offset,
                            y + 0.5f - offset,
                            0.22f,
                            16);
                }
            }
        }
    }

    private void drawGrid(
            VisibleRange range) {

        shapes.setColor(GRID);

        for (int x = range.minX(); x <= range.maxX() + 1; x++) {
            shapes.line(
                    x,
                    range.minY(),
                    x,
                    range.maxY() + 1f);
        }

        for (int y = range.minY(); y <= range.maxY() + 1; y++) {
            shapes.line(
                    range.minX(),
                    y,
                    range.maxX() + 1f,
                    y);
        }
    }

    private void drawRampDirections(
            VisibleRange range,
            int standingZ,
            boolean dimmed) {

        int terrainZ = standingZ - 1;

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                Shape shape = view.geometry().find(
                        x,
                        y,
                        terrainZ);

                if (!(shape instanceof RampShape)) {
                    continue;
                }

                setLayerColor(
                        RAMP_ARROW,
                        dimmed);

                if (shape == RampShape.POSITIVE_X) {
                    drawArrow(x, y, 1, 0, 0.34f);
                } else if (shape == RampShape.NEGATIVE_X) {
                    drawArrow(x, y, -1, 0, 0.34f);
                } else if (shape == RampShape.POSITIVE_Y) {
                    drawArrow(x, y, 0, 1, 0.34f);
                } else if (shape == RampShape.NEGATIVE_Y) {
                    drawArrow(x, y, 0, -1, 0.34f);
                }
            }
        }
    }

    private void drawCellSelection() {
        if (selectedCell == null) {
            return;
        }

        shapes.setColor(SELECTED);
        shapes.rect(
                selectedCell.x() + 0.03f,
                selectedCell.y() + 0.03f,
                0.94f,
                0.94f);
    }

    private void drawTransitionOverlay() {
        if (selectedCell == null) {
            return;
        }

        int mask = view.navigation().transitions(
                selectedCell.x(),
                selectedCell.y(),
                selectedCell.z());

        for (int dz = -1; dz <= 1; dz++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    if (!TransitionMask.contains(
                            mask,
                            dx,
                            dy,
                            dz)) {
                        continue;
                    }

                    shapes.setColor(
                            dz > 0
                                    ? TRANSITION_UP
                                    : dz < 0
                                            ? TRANSITION_DOWN
                                            : TRANSITION_FLAT);

                    drawTransitionArrow(
                            selectedCell.x(),
                            selectedCell.y(),
                            dx,
                            dy,
                            dz);
                }
            }
        }
    }

    private void drawTransitionArrow(
            int x,
            int y,
            int dx,
            int dy,
            int dz) {

        float startX = x + 0.5f;
        float startY = y + 0.5f;

        float length = dx == 0 && dy == 0
                ? 0.18f
                : 0.38f;

        float magnitude = (float) Math.sqrt(
                dx * dx + dy * dy);

        float unitX = magnitude == 0f
                ? 0f
                : dx / magnitude;
        float unitY = magnitude == 0f
                ? 0f
                : dy / magnitude;

        float endX = startX + unitX * length;
        float endY = startY + unitY * length;

        shapes.line(
                startX,
                startY,
                endX,
                endY);

        if (magnitude == 0f) {
            shapes.circle(
                    startX,
                    startY,
                    dz > 0 ? 0.11f : 0.07f,
                    12);
        } else {
            drawArrowHead(
                    endX,
                    endY,
                    unitX,
                    unitY,
                    0.10f);
        }
    }

    private void drawSelectedObject() {
        if (selectedObject == null
                || !view.transforms().has(selectedObject)
                || view.transforms().z(selectedObject) != selectedZ) {
            return;
        }

        int x = view.transforms().x(selectedObject);
        int y = view.transforms().y(selectedObject);

        shapes.setColor(SELECTED);
        shapes.circle(
                x + 0.5f,
                y + 0.5f,
                0.31f,
                20);
    }

    private void drawHud() {
        batch.begin();

        font.setColor(Color.WHITE);
        float top = Gdx.graphics.getHeight() - 14f;

        font.draw(
                batch,
                "tick=" + runtime.time().tick()
                        + "   Z=" + selectedZ
                        + "   " + (time.running() ? "RUN x1" : "PAUSED"),
                12f,
                top);

        font.draw(
                batch,
                "Space run/pause | N single tick | PgUp/PgDn Z | WASD pan | wheel zoom",
                12f,
                top - 20f);

        font.draw(
                batch,
                "standing plane Z=" + selectedZ
                        + " (support terrain Z=" + (selectedZ - 1) + ")",
                12f,
                top - 40f);

        if (selectedCell != null) {
            int mask = view.navigation().transitions(
                    selectedCell.x(),
                    selectedCell.y(),
                    selectedCell.z());

            font.draw(
                    batch,
                    "cell: (" + selectedCell.x()
                            + ", " + selectedCell.y()
                            + ", " + selectedCell.z()
                            + ") transitions="
                            + Integer.bitCount(mask),
                    12f,
                    top - 66f);
        }

        if (selectedObject != null) {
            WorldObject object = view.objects().get(selectedObject);

            if (object != null
                    && view.transforms().has(selectedObject)) {
                font.draw(
                        batch,
                        "object: " + selectedObject,
                        12f,
                        top - 90f);
                font.draw(
                        batch,
                        "definition: " + object.definitionId(),
                        12f,
                        top - 108f);
                font.draw(
                        batch,
                        "XYZ: "
                                + view.transforms().x(selectedObject)
                                + ", "
                                + view.transforms().y(selectedObject)
                                + ", "
                                + view.transforms().z(selectedObject),
                        12f,
                        top - 126f);
            }
        }

        batch.end();
    }

    private void drawArrow(
            int cellX,
            int cellY,
            int dx,
            int dy,
            float length) {

        float startX = cellX + 0.5f;
        float startY = cellY + 0.5f;
        float endX = startX + dx * length;
        float endY = startY + dy * length;

        shapes.line(
                startX,
                startY,
                endX,
                endY);
        drawArrowHead(
                endX,
                endY,
                dx,
                dy,
                0.11f);
    }

    private void drawArrowHead(
            float endX,
            float endY,
            float unitX,
            float unitY,
            float size) {

        float perpendicularX = -unitY;
        float perpendicularY = unitX;

        float backX = endX - unitX * size;
        float backY = endY - unitY * size;

        shapes.line(
                endX,
                endY,
                backX + perpendicularX * size,
                backY + perpendicularY * size);
        shapes.line(
                endX,
                endY,
                backX - perpendicularX * size,
                backY - perpendicularY * size);
    }

    private void setLayerColor(
            Color base,
            boolean dimmed) {

        if (!dimmed) {
            shapes.setColor(base);
            return;
        }

        float gray = (base.r + base.g + base.b) / 3f;
        shapes.setColor(
                (base.r * 0.35f + gray * 0.10f),
                (base.g * 0.35f + gray * 0.10f),
                (base.b * 0.35f + gray * 0.10f),
                1f);
    }

    private void clearSelection() {
        selectedCell = null;
        selectedObject = null;
    }

    private record VisibleRange(
            int minX,
            int maxX,
            int minY,
            int maxY) {
    }

    private record CellSelection(
            int x,
            int y,
            int z) {
    }
}
