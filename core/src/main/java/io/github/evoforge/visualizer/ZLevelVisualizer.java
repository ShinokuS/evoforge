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
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector3;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationStepper;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.visualizer.render.LandscapeRenderer;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/**
 * Full top-down debug presentation of an authoritative horizontal Z slice.
 *
 * <p>Visibility is derived from world volume rather than absolute Z labels:
 * solid mass intersects the cut, open surfaces remain bright when exposed,
 * covered cave space darkens with cover/exposure distance, and lower surfaces
 * remain visible through genuinely open drops. Presentation owns no simulation
 * mutation capability.</p>
 */
public final class ZLevelVisualizer extends InputAdapter {

    private static final float BASE_VIEW_WIDTH = 28f;
    private static final float PAN_SPEED = 8f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 4f;

    private static final int[] LOWER_DEPTH_OPTIONS = {0, 1, 4, 8};
    private static final int INSPECT_EXPOSURE_DISTANCE = 12;

    private static final Color BACKGROUND =
            new Color(0.045f, 0.055f, 0.065f, 1f);
    private static final Color GRID_SUBTLE =
            new Color(0.12f, 0.16f, 0.14f, 1f);
    private static final Color GRID_DEBUG =
            new Color(0.42f, 0.48f, 0.44f, 1f);
    private static final Color ACTIVE_SLICE_RIM =
            new Color(0.82f, 0.85f, 0.69f, 1f);
    private static final Color ACTIVE_SLICE_SHADOW =
            new Color(0.12f, 0.14f, 0.11f, 1f);
    private static final Color RAMP_ARROW =
            new Color(1f, 0.88f, 0.28f, 1f);
    private static final Color OBJECT_EVEN =
            new Color(0.30f, 0.78f, 0.94f, 1f);
    private static final Color OBJECT_ODD =
            new Color(0.90f, 0.44f, 0.56f, 1f);
    private static final Color SELECTED =
            new Color(1f, 0.93f, 0.34f, 1f);
    private static final Color TRANSITION_FLAT =
            new Color(0.30f, 0.88f, 0.76f, 1f);
    private static final Color TRANSITION_UP =
            new Color(0.50f, 0.92f, 0.43f, 1f);
    private static final Color TRANSITION_DOWN =
            new Color(1f, 0.56f, 0.26f, 1f);
    private static final Color PANEL =
            new Color(0.035f, 0.045f, 0.052f, 0.96f);
    private static final Color PANEL_BORDER =
            new Color(0.24f, 0.30f, 0.31f, 1f);

    private final SimulationView view;
    private final SimulationTime simulationTime;
    private final VisualizerTimeController time;

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 hudProjection = new Matrix4();
    private final Vector3 pick = new Vector3();
    private final ProceduralLandscapePack landscapePack =
            new ProceduralLandscapePack();
    private final ProceduralSliceArt sliceArt =
            new ProceduralSliceArt();
    private final LandscapeSliceResolver sliceResolver;
    private final LandscapeRenderer landscapeRenderer;

    private float cameraTargetX;
    private float cameraTargetY;
    private int selectedZ = 1;
    private int gridMode = 1;
    private int lowerDepthIndex = 3;
    private boolean showTransitions;
    private boolean showShapeDirections;
    private CellSelection selectedCell;
    private ObjectId selectedObject;

    public ZLevelVisualizer(
            SimulationView view,
            SimulationTime simulationTime,
            SimulationStepper stepper) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (simulationTime == null) {
            throw new IllegalArgumentException(
                    "simulationTime must not be null");
        }
        if (stepper == null) {
            throw new IllegalArgumentException("stepper must not be null");
        }

        this.view = view;
        this.simulationTime = simulationTime;
        time = new VisualizerTimeController(stepper, 0.25f);
        sliceResolver = new LandscapeSliceResolver(view);
        landscapeRenderer = new LandscapeRenderer(
                view,
                landscapePack,
                sliceArt,
                sliceResolver);

        cameraTargetX = 0f;
        cameraTargetY = 0f;
        camera.position.set(0f, 0f, 0f);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.input.setInputProcessor(this);
    }

    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        updateCameraTarget(delta);
        time.update(delta);

        Gdx.gl.glClearColor(
                BACKGROUND.r,
                BACKGROUND.g,
                BACKGROUND.b,
                BACKGROUND.a);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        snapRenderCamera(
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
        camera.update();
        VisibleRange range = visibleRange();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        landscapeRenderer.draw(
                batch,
                range.minX(),
                range.maxX(),
                range.minY(),
                range.maxY(),
                selectedZ,
                lowerDepth());
        batch.end();

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawActiveSliceContour(range);
        drawObjects(range);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        drawGrid(range);
        if (showShapeDirections) {
            drawRampDirections(range, selectedZ - 1);
            drawRampDirections(range, selectedZ);
        }
        drawCellSelection();
        if (showTransitions) {
            drawTransitionOverlay();
        }
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
        snapRenderCamera(width, height);
        camera.update();

        hudProjection.setToOrtho2D(
                0f,
                0f,
                width,
                height);
    }

    public void dispose() {
        if (Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
        sliceArt.dispose();
        landscapePack.dispose();
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
            case Input.Keys.G -> {
                gridMode = (gridMode + 1) % 3;
                return true;
            }
            case Input.Keys.F2 -> {
                showTransitions = !showTransitions;
                return true;
            }
            case Input.Keys.F3 -> {
                showShapeDirections = !showShapeDirections;
                return true;
            }
            case Input.Keys.F4 -> {
                lowerDepthIndex = (lowerDepthIndex + 1)
                        % LOWER_DEPTH_OPTIONS.length;
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

        pick.set(screenX, screenY, 0f);
        camera.unproject(pick);

        int x = MathUtils.floor(pick.x);
        int y = MathUtils.floor(pick.y);

        selectedCell = new CellSelection(x, y, selectedZ);

        int count = view.cells().objectCount(x, y, selectedZ);
        selectedObject = count == 0
                ? null
                : view.cells().objectAt(x, y, selectedZ, 0);

        return true;
    }

    private void updateCameraTarget(
            float delta) {

        float distance = PAN_SPEED * camera.zoom * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.A)) {
            cameraTargetX -= distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D)) {
            cameraTargetX += distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S)) {
            cameraTargetY -= distance;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W)) {
            cameraTargetY += distance;
        }
    }

    private void snapRenderCamera(
            int width,
            int height) {

        camera.position.x = CameraPixelSnap.axis(
                cameraTargetX,
                camera.viewportWidth * camera.zoom,
                Math.max(1, width));
        camera.position.y = CameraPixelSnap.axis(
                cameraTargetY,
                camera.viewportHeight * camera.zoom,
                Math.max(1, height));
        camera.position.z = 0f;
    }

    private VisibleRange visibleRange() {
        float halfWidth = camera.viewportWidth * camera.zoom * 0.5f;
        float halfHeight = camera.viewportHeight * camera.zoom * 0.5f;

        return new VisibleRange(
                MathUtils.floor(camera.position.x - halfWidth) - 1,
                MathUtils.ceil(camera.position.x + halfWidth) + 1,
                MathUtils.floor(camera.position.y - halfHeight) - 1,
                MathUtils.ceil(camera.position.y + halfHeight) + 1);
    }

    private void drawActiveSliceContour(
            VisibleRange range) {

        float pixel = worldUnitsPerPixel();
        float shadowThickness = pixel * 1.25f;
        float rimThickness = pixel;

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                if (!sliceResolver.isCurrentSurface(x, y, selectedZ)) {
                    continue;
                }

                boolean north = !sliceResolver.isCurrentSurface(
                        x, y + 1, selectedZ);
                boolean east = !sliceResolver.isCurrentSurface(
                        x + 1, y, selectedZ);
                boolean south = !sliceResolver.isCurrentSurface(
                        x, y - 1, selectedZ);
                boolean west = !sliceResolver.isCurrentSurface(
                        x - 1, y, selectedZ);

                if (!north && !east && !south && !west) {
                    continue;
                }

                shapes.setColor(ACTIVE_SLICE_SHADOW);
                drawContourEdges(
                        x,
                        y,
                        north,
                        east,
                        south,
                        west,
                        shadowThickness,
                        true);

                shapes.setColor(ACTIVE_SLICE_RIM);
                drawContourEdges(
                        x,
                        y,
                        north,
                        east,
                        south,
                        west,
                        rimThickness,
                        false);
            }
        }
    }

    private void drawContourEdges(
            int x,
            int y,
            boolean north,
            boolean east,
            boolean south,
            boolean west,
            float thickness,
            boolean outside) {

        if (north) {
            shapes.rect(
                    x,
                    outside ? y + 1f : y + 1f - thickness,
                    1f,
                    thickness);
        }
        if (east) {
            shapes.rect(
                    outside ? x + 1f : x + 1f - thickness,
                    y,
                    thickness,
                    1f);
        }
        if (south) {
            shapes.rect(
                    x,
                    outside ? y - thickness : y,
                    1f,
                    thickness);
        }
        if (west) {
            shapes.rect(
                    outside ? x - thickness : x,
                    y,
                    thickness,
                    1f);
        }
    }

    private float worldUnitsPerPixel() {
        int width = Math.max(1, Gdx.graphics.getWidth());
        int height = Math.max(1, Gdx.graphics.getHeight());
        float horizontal = camera.viewportWidth * camera.zoom / width;
        float vertical = camera.viewportHeight * camera.zoom / height;
        return Math.max(horizontal, vertical);
    }

    private void drawObjects(
            VisibleRange range) {

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                int count = view.cells().objectCount(x, y, selectedZ);

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

        if (gridMode == 0) {
            return;
        }

        shapes.setColor(gridMode == 1 ? GRID_SUBTLE : GRID_DEBUG);

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
            int terrainZ) {

        shapes.setColor(RAMP_ARROW);

        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                Shape shape = view.geometry().find(x, y, terrainZ);

                if (!(shape instanceof RampShape)) {
                    continue;
                }

                if (shape == RampShape.POSITIVE_X) {
                    drawArrow(x, y, 1, 0, 0.35f);
                } else if (shape == RampShape.NEGATIVE_X) {
                    drawArrow(x, y, -1, 0, 0.35f);
                } else if (shape == RampShape.POSITIVE_Y) {
                    drawArrow(x, y, 0, 1, 0.35f);
                } else if (shape == RampShape.NEGATIVE_Y) {
                    drawArrow(x, y, 0, -1, 0.35f);
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
                    if (!TransitionMask.contains(mask, dx, dy, dz)) {
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
        float length = dx == 0 && dy == 0 ? 0.18f : 0.38f;
        float magnitude = (float) Math.sqrt(dx * dx + dy * dy);
        float unitX = magnitude == 0f ? 0f : dx / magnitude;
        float unitY = magnitude == 0f ? 0f : dy / magnitude;
        float endX = startX + unitX * length;
        float endY = startY + unitY * length;

        shapes.line(startX, startY, endX, endY);

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
        shapes.circle(x + 0.5f, y + 0.5f, 0.31f, 20);
    }

    private void drawHud() {
        int width = Gdx.graphics.getWidth();
        int height = Gdx.graphics.getHeight();

        float margin = 12f;
        float statusWidth = Math.min(630f, width - margin * 2f);
        float statusHeight = 104f;
        float statusX = margin;
        float statusY = height - margin - statusHeight;

        boolean hasInspector = selectedCell != null;
        float inspectorWidth = Math.min(380f, width - margin * 2f);
        float inspectorHeight = selectedObject == null ? 150f : 216f;
        float inspectorX = width - margin - inspectorWidth;
        float inspectorY = height - margin - inspectorHeight;

        if (hasInspector && inspectorX < statusX + statusWidth + margin) {
            inspectorX = margin;
            inspectorY = statusY - margin - inspectorHeight;
        }

        shapes.setProjectionMatrix(hudProjection);
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

        batch.setProjectionMatrix(hudProjection);
        batch.begin();
        font.setColor(Color.WHITE);

        float textX = statusX + 12f;
        float top = statusY + statusHeight - 12f;
        font.draw(
                batch,
                "STATUS   tick " + simulationTime.tick()
                        + "   Z " + selectedZ
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
                "WASD pan | wheel zoom | G grid " + gridLabel(),
                textX,
                top - 44f);
        font.draw(
                batch,
                "F2 transitions " + onOff(showTransitions)
                        + " | F3 ramps " + onOff(showShapeDirections)
                        + " | F4 lower depth " + lowerDepth(),
                textX,
                top - 66f);

        if (hasInspector) {
            drawInspectorText(
                    inspectorX + 12f,
                    inspectorY + inspectorHeight - 12f);
        }

        batch.end();
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
            float x,
            float top) {

        LandscapeSliceResolver.Cell slice = sliceResolver.analyze(
                selectedCell.x(),
                selectedCell.x(),
                selectedCell.y(),
                selectedCell.y(),
                selectedCell.z(),
                lowerDepth(),
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
        font.draw(
                batch,
                "slice: " + sliceLabel(slice),
                x,
                top - 22f);
        font.draw(
                batch,
                "context: " + contextLabel(slice),
                x,
                top - 44f);
        font.draw(
                batch,
                "shape: " + shapeLabel(slice.shape()),
                x,
                top - 66f);
        font.draw(
                batch,
                "transitions: " + Integer.bitCount(transitions),
                x,
                top - 88f);

        if (selectedObject == null) {
            return;
        }

        WorldObject object = view.objects().get(selectedObject);
        if (object == null || !view.transforms().has(selectedObject)) {
            return;
        }

        font.draw(batch, "OBJECT   " + selectedObject, x, top - 118f);
        font.draw(
                batch,
                "definition: " + object.definitionId(),
                x,
                top - 140f);
        font.draw(
                batch,
                "XYZ: "
                        + view.transforms().x(selectedObject)
                        + ", "
                        + view.transforms().y(selectedObject)
                        + ", "
                        + view.transforms().z(selectedObject),
                x,
                top - 162f);
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

    private static String shapeLabel(
            Shape shape) {

        if (shape == RampShape.POSITIVE_X) {
            return "Ramp +X";
        }
        if (shape == RampShape.NEGATIVE_X) {
            return "Ramp -X";
        }
        if (shape == RampShape.POSITIVE_Y) {
            return "Ramp +Y";
        }
        if (shape == RampShape.NEGATIVE_Y) {
            return "Ramp -Y";
        }
        return shape == null ? "none" : shape.getClass().getSimpleName();
    }

    private String gridLabel() {
        return switch (gridMode) {
            case 0 -> "OFF";
            case 1 -> "SUBTLE";
            default -> "DEBUG";
        };
    }

    private int lowerDepth() {
        return LOWER_DEPTH_OPTIONS[lowerDepthIndex];
    }

    private static String onOff(
            boolean value) {

        return value ? "ON" : "OFF";
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

        shapes.line(startX, startY, endX, endY);
        drawArrowHead(endX, endY, dx, dy, 0.11f);
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
