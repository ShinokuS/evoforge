package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilHydrology;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerTimeController;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;
import io.github.evoforge.visualizer.visual.WaterOpticalDepthResolver;

import java.util.ArrayList;
import java.util.List;

/** Runtime status plus a content-sized selected cell/object card. */
public final class VisualizerPrimaryHudRenderer {

    private static final Color PANEL = new Color(0.030f, 0.040f, 0.047f, 0.97f);
    private static final Color BORDER = new Color(0.28f, 0.35f, 0.35f, 1f);
    private static final Color TITLE = new Color(0.91f, 0.98f, 0.90f, 1f);
    private static final Color TEXT = new Color(0.94f, 0.97f, 0.95f, 1f);
    private static final Color MUTED = new Color(0.70f, 0.77f, 0.73f, 1f);
    private static final Color MOVE = new Color(0.48f, 0.84f, 1.00f, 1f);
    private static final Color BAR_BACKGROUND = new Color(0.13f, 0.17f, 0.17f, 1f);
    private static final Color WATER_FILL = new Color(0.35f, 0.72f, 0.88f, 1f);
    private static final Color SOIL_FILL = new Color(0.67f, 0.55f, 0.29f, 1f);
    private static final float MARGIN = 12f;
    private static final float PAD_X = 14f;
    private static final float PAD_Y = 9f;
    private static final float ROW_GAP = 3f;
    private static final float BAR_GAP = 3f;
    private static final float BAR_HEIGHT = 8f;

    private final SimulationView view;
    private final SimulationTime simulationTime;
    private final VisualizerTimeController time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ObjectPresentationBindings presentations;
    private final SurfaceProjectionResolver surfaces;
    private final WaterOpticalDepthResolver waterDepth;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont titleFont;
    private final BitmapFont bodyFont;
    private final GlyphLayout layout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();
    private int width = 1;
    private int height = 1;
    private float lastSelectionPanelHeight;

    public VisualizerPrimaryHudRenderer(
            SimulationView view,
            SimulationTime simulationTime,
            VisualizerTimeController time,
            VisualizerState state,
            VisualizerCamera camera,
            ObjectPresentationBindings presentations,
            SurfaceProjectionResolver surfaces,
            VisualizerUiAssets ui) {
        if (view == null || simulationTime == null || time == null || state == null
                || camera == null || presentations == null || surfaces == null || ui == null) {
            throw new IllegalArgumentException("primary HUD dependencies must not be null");
        }
        this.view = view;
        this.simulationTime = simulationTime;
        this.time = time;
        this.state = state;
        this.camera = camera;
        this.presentations = presentations;
        this.surfaces = surfaces;
        this.waterDepth = new WaterOpticalDepthResolver(view);
        this.titleFont = ui.largeSubtitle();
        this.bodyFont = ui.largeList();
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
    }

    public void draw() {
        drawRuntimeStatus();
        drawSelectionCard();
    }

    /** Height reserved at the top-right for the selected cell/object card. */
    public float rightPanelReservedHeight() {
        return lastSelectionPanelHeight <= 0f ? 0f : lastSelectionPanelHeight + MARGIN;
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
    }

    private void drawRuntimeStatus() {
        String text = (time.running() ? "RUNNING" : "PAUSED")
                + "   Tick " + simulationTime.tick()
                + "   FPS " + Gdx.graphics.getFramesPerSecond()
                + "   Zoom " + camera.zoomLabel();
        float contentWidth = textWidth(titleFont, text);
        float panelWidth = fitWidth(contentWidth + PAD_X * 2f);
        float panelHeight = titleFont.getLineHeight() + PAD_Y * 2f;
        float x = MARGIN;
        float y = height - MARGIN - panelHeight;
        panel(x, y, panelWidth, panelHeight);

        batch.setProjectionMatrix(projection);
        batch.begin();
        titleFont.setColor(TEXT);
        titleFont.draw(batch, text, x + PAD_X, y + panelHeight - PAD_Y);
        batch.end();
    }

    private void drawSelectionCard() {
        VisualizerState.CellSelection cell = state.selectedCell();
        if (cell == null) {
            lastSelectionPanelHeight = 0f;
            return;
        }

        List<Row> rows = selectionRows(cell);
        if (rows.isEmpty()) {
            lastSelectionPanelHeight = 0f;
            return;
        }

        float contentWidth = 1f;
        float contentHeight = 0f;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            BitmapFont font = row.title() ? titleFont : bodyFont;
            contentWidth = Math.max(contentWidth, textWidth(font, row.text()));
            contentHeight += rowHeight(row, font);
            if (i + 1 < rows.size()) contentHeight += ROW_GAP;
        }

        float panelWidth = fitWidth(contentWidth + PAD_X * 2f);
        float panelHeight = contentHeight + PAD_Y * 2f;
        lastSelectionPanelHeight = panelHeight;
        float x = Math.max(MARGIN, width - MARGIN - panelWidth);
        float y = Math.max(MARGIN, height - MARGIN - panelHeight);
        panel(x, y, panelWidth, panelHeight);
        drawMeterBars(rows, x, y, panelWidth, panelHeight);

        batch.setProjectionMatrix(projection);
        batch.begin();
        float baseline = y + panelHeight - PAD_Y;
        for (Row row : rows) {
            BitmapFont font = row.title() ? titleFont : bodyFont;
            font.setColor(row.color());
            font.draw(batch, row.text(), x + PAD_X, baseline);
            baseline -= rowHeight(row, font) + ROW_GAP;
        }
        batch.end();
    }

    private void drawMeterBars(
            List<Row> rows,
            float x,
            float y,
            float panelWidth,
            float panelHeight) {
        boolean hasMeter = false;
        for (Row row : rows) {
            if (row.meter()) {
                hasMeter = true;
                break;
            }
        }
        if (!hasMeter) return;

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        float baseline = y + panelHeight - PAD_Y;
        float barWidth = Math.max(1f, panelWidth - PAD_X * 2f);
        for (Row row : rows) {
            BitmapFont font = row.title() ? titleFont : bodyFont;
            if (row.meter()) {
                float barY = baseline - font.getLineHeight() - BAR_GAP - BAR_HEIGHT;
                shapes.setColor(BAR_BACKGROUND);
                shapes.rect(x + PAD_X, barY, barWidth, BAR_HEIGHT);
                shapes.setColor(row.fillColor());
                shapes.rect(
                        x + PAD_X,
                        barY,
                        barWidth * clamp01(row.fraction()),
                        BAR_HEIGHT);
            }
            baseline -= rowHeight(row, font) + ROW_GAP;
        }
        shapes.end();
    }

    private static float rowHeight(Row row, BitmapFont font) {
        return font.getLineHeight()
                + (row.meter() ? BAR_GAP + BAR_HEIGHT : 0f);
    }

    private List<Row> selectionRows(VisualizerState.CellSelection cell) {
        List<Row> rows = new ArrayList<>(15);
        ObjectId selected = state.selectedObject();
        if (selected != null && view.objects().isAlive(selected) && view.transforms().has(selected)) {
            WorldObject object = view.objects().get(selected);
            ObjectPresentation presentation = object == null ? null : presentations.get(object.definitionId());
            String name = presentation == null ? "Object" : presentation.displayName();
            rows.add(new Row(name, true, TITLE));
            rows.add(new Row("Position   " + view.transforms().x(selected) + ", "
                    + view.transforms().y(selected) + ", " + view.transforms().z(selected), false, TEXT));
            boolean moving = view.moveTo().isActive(selected);
            rows.add(new Row("Movement   " + (moving ? "Moving" : "Idle"), false, moving ? MOVE : MUTED));
        } else {
            rows.add(new Row("Cell " + cell.x() + ", " + cell.y() + ", " + cell.z(), true, TITLE));
        }

        appendCellRows(rows, cell);
        if (state.showTechnicalDetails()) appendTechnicalRows(rows, cell, selected);
        return rows;
    }

    private void appendCellRows(List<Row> rows, VisualizerState.CellSelection cell) {
        int x = cell.x();
        int y = cell.y();
        int selectedZ = cell.z();
        int terrainZ = selectedZ;
        LandscapeDefinitionId terrain;
        int waterZ = selectedZ;
        int waterAmount = 0;
        int opticalDepth = 0;

        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            SurfaceProjectionResolver.SurfaceCell surface = surfaces.resolve(x, y);
            terrainZ = surface.hasTerrain() ? surface.terrainZ() : SurfaceProjectionResolver.NO_Z;
            terrain = surface.hasTerrain() ? view.terrain().find(x, y, terrainZ) : null;
            opticalDepth = waterDepth.visibleDepth(x, y);
            if (surface.hasWater() && opticalDepth > 0) {
                waterZ = surface.waterZ();
                waterAmount = view.water().amount(x, y, waterZ);
            } else {
                waterZ = SurfaceProjectionResolver.NO_Z;
            }
        } else {
            terrain = view.terrain().find(x, y, terrainZ);
            if (terrain == null && terrainZ != Integer.MIN_VALUE) {
                terrainZ--;
                terrain = view.terrain().find(x, y, terrainZ);
            }
            waterAmount = view.water().amount(x, y, selectedZ);
        }

        rows.add(new Row("Cell   " + x + ", " + y + ", " + selectedZ, false, TEXT));
        rows.add(new Row(
                "Terrain   " + (terrain == null ? "none" : terrain + " @ z" + terrainZ),
                false,
                terrain == null ? MUTED : TEXT));

        if (waterAmount > 0) {
            Shape waterShape = view.geometry().find(x, y, waterZ);
            int waterCapacity = CellSpace.capacity(waterShape);
            String water = "Water fill   " + waterAmount + " / " + waterCapacity + " @ z" + waterZ;
            rows.add(new Row(
                    water,
                    false,
                    TEXT,
                    fraction(waterAmount, waterCapacity),
                    WATER_FILL));
            if (state.viewMode() == VisualizerViewMode.SURFACE) {
                rows.add(new Row("Water depth   " + opticalDepth, false, MUTED));
            }
        } else {
            rows.add(new Row("Water   none visible", false, MUTED));
        }

        if (terrain != null) {
            int moisture = view.soilMoisture().amount(x, y, terrainZ);
            SoilHydrology hydrology = view.soilHydrology().find(x, y, terrainZ);
            if (hydrology == null) {
                rows.add(new Row("Soil moisture   n/a (non-absorbing terrain)", false, MUTED));
            } else {
                rows.add(new Row(
                        "Soil moisture   " + moisture + " / " + hydrology.capacity(),
                        false,
                        TEXT,
                        fraction(moisture, hydrology.capacity()),
                        SOIL_FILL));
            }
        } else {
            rows.add(new Row("Soil moisture   n/a", false, MUTED));
        }
        rows.add(new Row("Objects   " + view.cells().objectCount(x, y, selectedZ), false, TEXT));
    }

    private void appendTechnicalRows(
            List<Row> rows,
            VisualizerState.CellSelection cell,
            ObjectId selected) {
        int x = cell.x();
        int y = cell.y();
        int z = cell.z();
        int terrainZ;
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            terrainZ = surfaces.terrainZ(x, y);
        } else if (view.terrain().find(x, y, z) != null) {
            terrainZ = z;
        } else {
            terrainZ = z == Integer.MIN_VALUE ? SurfaceProjectionResolver.NO_Z : z - 1;
        }
        int waterCellZ = state.viewMode() == VisualizerViewMode.SURFACE
                ? surfaces.standingZ(x, y)
                : z;

        if (terrainZ != SurfaceProjectionResolver.NO_Z) {
            SoilHydrology hydrology = view.soilHydrology().find(x, y, terrainZ);
            if (hydrology != null) {
                rows.add(new Row("Soil infiltration   " + hydrology.infiltrationLimit(), false, MUTED));
            }
        }
        if (waterCellZ != SurfaceProjectionResolver.NO_Z) {
            rows.add(new Row(
                    "Surface storage   " + view.surfaceWaterStorage().capacityAtWaterCell(x, y, waterCellZ),
                    false,
                    MUTED));
        }

        Shape shape = view.geometry().find(x, y, z);
        rows.add(new Row("Shape   " + (shape == null ? "open" : shape.getClass().getSimpleName()), false, MUTED));
        rows.add(new Row("Occupancy   " + view.occupancy().state(x, y, z), false, MUTED));
        rows.add(new Row("Transitions   "
                + Integer.bitCount(view.navigation().transitions(x, y, z)), false, MUTED));
        if (selected != null) rows.add(new Row("Object id   " + selected, false, MUTED));
    }

    private float fitWidth(float requested) {
        return Math.max(1f, Math.min(requested, Math.max(1f, width - MARGIN * 2f)));
    }

    private float textWidth(BitmapFont font, String text) {
        layout.setText(font, text == null ? "" : text);
        return layout.width;
    }

    private void panel(float x, float y, float panelWidth, float panelHeight) {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL);
        shapes.rect(x, y, panelWidth, panelHeight);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(x, y, panelWidth, panelHeight);
        shapes.end();
    }

    private static float fraction(int amount, int capacity) {
        if (amount <= 0 || capacity <= 0) return 0f;
        return clamp01(amount / (float) capacity);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private record Row(
            String text,
            boolean title,
            Color color,
            float fraction,
            Color fillColor) {

        private Row(String text, boolean title, Color color) {
            this(text, title, color, -1f, null);
        }

        private boolean meter() {
            return fillColor != null && fraction >= 0f;
        }
    }
}
