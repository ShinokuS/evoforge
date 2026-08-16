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
import io.github.evoforge.simulation.world.agent.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.landscape.soil.SoilProperties;
import io.github.evoforge.simulation.world.landscape.water.WaterSystem;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthStatus;
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

/** Runtime status plus a content-sized selected object/terrain inspector. */
public final class VisualizerPrimaryHudRenderer {

    private static final Color PANEL = new Color(0.030f, 0.040f, 0.047f, 0.97f);
    private static final Color BORDER = new Color(0.28f, 0.35f, 0.35f, 1f);
    private static final Color TITLE = new Color(0.91f, 0.98f, 0.90f, 1f);
    private static final Color TEXT = new Color(0.94f, 0.97f, 0.95f, 1f);
    private static final Color MUTED = new Color(0.70f, 0.77f, 0.73f, 1f);
    private static final Color MOVE = new Color(0.48f, 0.84f, 1.00f, 1f);
    private static final Color TAB_ACTIVE = new Color(0.16f, 0.25f, 0.24f, 1f);
    private static final Color TAB_INACTIVE = new Color(0.075f, 0.095f, 0.10f, 1f);
    private static final Color BAR_BACKGROUND = new Color(0.13f, 0.17f, 0.17f, 1f);
    private static final Color NEED_FILL = new Color(0.90f, 0.57f, 0.22f, 1f);
    private static final Color ACTION_FILL = new Color(0.34f, 0.67f, 0.89f, 1f);
    private static final Color RESOURCE_FILL = new Color(0.33f, 0.72f, 0.35f, 1f);
    private static final Color WATER_FILL = new Color(0.35f, 0.72f, 0.88f, 1f);
    private static final Color SOIL_FILL = new Color(0.67f, 0.55f, 0.29f, 1f);
    private static final float MARGIN = 12f;
    private static final float PAD_X = 14f;
    private static final float PAD_Y = 9f;
    private static final float ROW_GAP = 3f;
    private static final float BAR_GAP = 3f;
    private static final float BAR_HEIGHT = 8f;
    private static final float TAB_HEIGHT = 28f;
    private static final float TAB_GAP = 8f;
    private static final float MIN_TABBED_CONTENT_WIDTH = 250f;

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
    private TabBounds objectTabBounds = TabBounds.NONE;
    private TabBounds terrainTabBounds = TabBounds.NONE;

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

    /** Returns the inspector tab under one libGDX screen-space click, or null. */
    public VisualizerState.InspectorTab tabAt(int screenX, int screenY) {
        float uiY = height - screenY;
        if (objectTabBounds.contains(screenX, uiY)) return VisualizerState.InspectorTab.OBJECT;
        if (terrainTabBounds.contains(screenX, uiY)) return VisualizerState.InspectorTab.TERRAIN;
        return null;
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
            clearTabBounds();
            lastSelectionPanelHeight = 0f;
            return;
        }

        ObjectId selected = validSelectedObject();
        boolean tabbed = selected != null;
        List<Row> rows = selectionRows(cell, selected);
        if (rows.isEmpty()) {
            clearTabBounds();
            lastSelectionPanelHeight = 0f;
            return;
        }

        float contentWidth = tabbed ? MIN_TABBED_CONTENT_WIDTH : 1f;
        float contentHeight = 0f;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            BitmapFont font = row.title() ? titleFont : bodyFont;
            contentWidth = Math.max(contentWidth, textWidth(font, row.text()));
            contentHeight += rowHeight(row, font);
            if (i + 1 < rows.size()) contentHeight += ROW_GAP;
        }

        float tabBlockHeight = tabbed ? TAB_HEIGHT + TAB_GAP : 0f;
        float panelWidth = fitWidth(contentWidth + PAD_X * 2f);
        float panelHeight = contentHeight + tabBlockHeight + PAD_Y * 2f;
        lastSelectionPanelHeight = panelHeight;
        float x = Math.max(MARGIN, width - MARGIN - panelWidth);
        float y = Math.max(MARGIN, height - MARGIN - panelHeight);
        panel(x, y, panelWidth, panelHeight);
        if (tabbed) drawTabBackgrounds(x, y, panelWidth, panelHeight);
        else clearTabBounds();
        drawMeterBars(rows, x, y, panelWidth, panelHeight, tabBlockHeight);

        batch.setProjectionMatrix(projection);
        batch.begin();
        if (tabbed) drawTabLabels(selected);
        float baseline = y + panelHeight - PAD_Y - tabBlockHeight;
        for (Row row : rows) {
            BitmapFont font = row.title() ? titleFont : bodyFont;
            font.setColor(row.color());
            font.draw(batch, row.text(), x + PAD_X, baseline);
            baseline -= rowHeight(row, font) + ROW_GAP;
        }
        batch.end();
    }

    private void drawTabBackgrounds(float x, float y, float panelWidth, float panelHeight) {
        float contentWidth = Math.max(1f, panelWidth - PAD_X * 2f);
        float tabWidth = contentWidth / 2f;
        float tabY = y + panelHeight - PAD_Y - TAB_HEIGHT;
        objectTabBounds = new TabBounds(x + PAD_X, tabY, tabWidth, TAB_HEIGHT);
        terrainTabBounds = new TabBounds(x + PAD_X + tabWidth, tabY, tabWidth, TAB_HEIGHT);

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(state.inspectorTab() == VisualizerState.InspectorTab.OBJECT
                ? TAB_ACTIVE : TAB_INACTIVE);
        shapes.rect(objectTabBounds.x(), objectTabBounds.y(), objectTabBounds.width(), objectTabBounds.height());
        shapes.setColor(state.inspectorTab() == VisualizerState.InspectorTab.TERRAIN
                ? TAB_ACTIVE : TAB_INACTIVE);
        shapes.rect(terrainTabBounds.x(), terrainTabBounds.y(), terrainTabBounds.width(), terrainTabBounds.height());
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(objectTabBounds.x(), objectTabBounds.y(), objectTabBounds.width(), objectTabBounds.height());
        shapes.rect(terrainTabBounds.x(), terrainTabBounds.y(), terrainTabBounds.width(), terrainTabBounds.height());
        shapes.end();
    }

    private void drawTabLabels(ObjectId selected) {
        String objectLabel = selectedObjectName(selected);
        bodyFont.setColor(state.inspectorTab() == VisualizerState.InspectorTab.OBJECT ? TITLE : MUTED);
        drawCentered(bodyFont, objectLabel, objectTabBounds);
        bodyFont.setColor(state.inspectorTab() == VisualizerState.InspectorTab.TERRAIN ? TITLE : MUTED);
        drawCentered(bodyFont, "Terrain", terrainTabBounds);
    }

    private void drawCentered(BitmapFont font, String text, TabBounds bounds) {
        layout.setText(font, text == null ? "" : text);
        float textX = bounds.x() + Math.max(0f, (bounds.width() - layout.width) * 0.5f);
        float baseline = bounds.y() + (bounds.height() + font.getLineHeight()) * 0.5f - 2f;
        font.draw(batch, text, textX, baseline);
    }

    private void drawMeterBars(
            List<Row> rows,
            float x,
            float y,
            float panelWidth,
            float panelHeight,
            float topInset) {
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
        float baseline = y + panelHeight - PAD_Y - topInset;
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

    private List<Row> selectionRows(
            VisualizerState.CellSelection cell,
            ObjectId selected) {
        if (selected != null && state.inspectorTab() == VisualizerState.InspectorTab.OBJECT) {
            return objectRows(selected);
        }
        return terrainRows(cell, selected);
    }

    private List<Row> objectRows(ObjectId selected) {
        List<Row> rows = new ArrayList<>(18);
        WorldObject object = view.objects().get(selected);
        if (object == null || !view.transforms().has(selected)) return rows;

        rows.add(new Row(selectedObjectName(selected), true, TITLE));
        rows.add(new Row("Position   " + view.transforms().x(selected) + ", "
                + view.transforms().y(selected) + ", " + view.transforms().z(selected), false, TEXT));
        boolean moving = view.moveTo().isActive(selected);
        rows.add(new Row("Movement   " + (moving ? "Moving" : "Idle"), false, moving ? MOVE : MUTED));

        appendAgentRows(rows, selected);
        appendNeedRows(rows, selected);
        appendResourceRows(rows, selected);

        if (state.showTechnicalDetails()) {
            rows.add(new Row("Object id   " + selected, false, MUTED));
            rows.add(new Row("Definition   " + object.definitionId(), false, MUTED));
            String facing = view.orientations().has(selected)
                    ? view.orientations().facing(selected).toString()
                    : "n/a";
            rows.add(new Row("Facing   " + facing, false, MUTED));
            AgentDecisionTrace decision = view.agents().lastDecision(selected);
            if (decision != null) {
                rows.add(new Row("Candidates   " + decision.candidates().size()
                        + " at t" + decision.tick(), false, MUTED));
            }
        }
        return rows;
    }

    private void appendAgentRows(List<Row> rows, ObjectId selected) {
        AgentIntentTrace intent = view.agents().currentIntent(selected);
        AgentSearchTrace search = view.searches().currentSearch(selected);
        AgentDecisionTrace decision = view.agents().lastDecision(selected);
        if (intent == null && search == null && decision == null) return;

        if (intent != null) {
            rows.add(new Row("Activity   " + activityLabel(intent.phase()), false, TEXT));
            String target = targetLabel(intent.targetKey());
            if (target != null) rows.add(new Row("Target   " + target, false, MUTED));
            if (intent.phase() == AgentIntentPhase.USING_OPPORTUNITY
                    && intent.expectedCompletionTick() > intent.startedTick()) {
                rows.add(new Row(
                        "Current action   " + percentText(intent.startedTick(), intent.expectedCompletionTick()),
                        false,
                        TEXT,
                        progressFraction(intent.startedTick(), intent.expectedCompletionTick()),
                        ACTION_FILL));
            }
            return;
        }

        if (search != null) {
            rows.add(new Row("Activity   Searching for " + humanize(search.motivation()), false, TEXT));
            rows.add(new Row("Search   " + search.status() + " | headings " + search.headingsObserved(), false, MUTED));
            return;
        }

        if (decision != null && decision.selected() != null) {
            rows.add(new Row("Activity   Deciding / ready", false, TEXT));
            rows.add(new Row("Last choice   " + humanize(decision.selected().motivation())
                    + " | utility " + decision.selected().utility(), false, MUTED));
        } else {
            rows.add(new Row("Activity   Idle / no current opportunity", false, MUTED));
        }
    }

    private void appendNeedRows(List<Row> rows, ObjectId selected) {
        int count = view.needs().needCount(selected);
        for (int index = 0; index < count; index++) {
            NeedId needId = view.needs().needAt(selected, index);
            long level = view.needs().level(selected, needId);
            long max = view.needs().maxLevel(selected, needId);
            rows.add(new Row(
                    humanize(needId.value()) + "   " + level + " / " + max,
                    false,
                    TEXT,
                    fraction(level, max),
                    NEED_FILL));
        }
    }

    private void appendResourceRows(List<Row> rows, ObjectId selected) {
        if (!view.consumableStocks().has(selected)) return;
        long quantity = view.consumableStocks().quantity(selected);
        long capacity = view.consumableStocks().capacity(selected);
        rows.add(new Row(
                "Biomass   " + quantity + " / " + capacity,
                false,
                TEXT,
                fraction(quantity, capacity),
                RESOURCE_FILL));
        if (!view.growth().has(selected)) return;
        GrowthStatus status = view.growth().status(selected);
        if (status == GrowthStatus.DORMANT_FULL) {
            rows.add(new Row("Growth   Full grown", false, MUTED));
        } else {
            long remaining = Math.max(0L, view.growth().nextEvaluationTick(selected) - simulationTime.tick());
            rows.add(new Row("Growth   Regrowing | next in " + remaining + " ticks", false, MUTED));
        }
    }

    private List<Row> terrainRows(
            VisualizerState.CellSelection cell,
            ObjectId selected) {
        List<Row> rows = new ArrayList<>(14);
        rows.add(new Row("Terrain / Cell", true, TITLE));
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
            SoilProperties properties = view.soilProperties().find(x, y, terrainZ);
            if (properties == null) {
                rows.add(new Row("Soil retained   n/a (non-porous terrain)", false, MUTED));
            } else {
                int retainedTotal = view.soilLiquids().totalAmount(x, y, terrainZ);
                int retainedWater = view.soilLiquids().amountOf(
                        WaterSystem.TYPE,
                        x,
                        y,
                        terrainZ);
                rows.add(new Row(
                        "Soil retained   " + retainedTotal + " / " + properties.capacity(),
                        false,
                        TEXT,
                        fraction(retainedTotal, properties.capacity()),
                        SOIL_FILL));
                rows.add(new Row(
                        "Retained Water   " + retainedWater,
                        false,
                        MUTED));
            }
        } else {
            rows.add(new Row("Soil retained   n/a", false, MUTED));
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
            SoilProperties properties = view.soilProperties().find(x, y, terrainZ);
            if (properties != null) {
                rows.add(new Row(
                        "Soil permeability   " + properties.permeability(),
                        false,
                        MUTED));
            }
        }
        if (waterCellZ != SurfaceProjectionResolver.NO_Z) {
            rows.add(new Row(
                    "Surface retention   " + view.surfaceRetention().capacityAt(
                            x,
                            y,
                            waterCellZ),
                    false,
                    MUTED));
        }

        Shape shape = view.geometry().find(x, y, z);
        rows.add(new Row("Shape   " + (shape == null ? "open" : shape.getClass().getSimpleName()), false, MUTED));
        rows.add(new Row("Occupancy   " + view.occupancy().state(x, y, z), false, MUTED));
        rows.add(new Row("Transitions   "
                + Integer.bitCount(view.navigation().transitions(x, y, z)), false, MUTED));
        if (selected != null) rows.add(new Row("Selected object   " + selected, false, MUTED));
    }

    private ObjectId validSelectedObject() {
        ObjectId selected = state.selectedObject();
        return selected != null && view.objects().isAlive(selected) && view.transforms().has(selected)
                ? selected
                : null;
    }

    private String selectedObjectName(ObjectId selected) {
        WorldObject object = selected == null ? null : view.objects().get(selected);
        ObjectPresentation presentation = object == null ? null : presentations.get(object.definitionId());
        return presentation == null ? "Object" : presentation.displayName();
    }

    private String targetLabel(String targetKey) {
        if (targetKey == null || targetKey.isBlank()) return null;
        if (targetKey.startsWith("liquid:")) {
            int at = targetKey.indexOf('@');
            int hash = targetKey.lastIndexOf('#');
            if (at >= 0 && hash > at) return "Water @ " + targetKey.substring(at + 1, hash);
            return "Water";
        }
        if (targetKey.startsWith("object:")) return "Object " + targetKey.substring("object:".length());
        return targetKey;
    }

    private static String activityLabel(AgentIntentPhase phase) {
        return switch (phase) {
            case MOVING_TO_OPPORTUNITY -> "Moving to opportunity";
            case USING_OPPORTUNITY -> "Using opportunity";
            case SEARCH_RELOCATION -> "Exploring / searching";
        };
    }

    private String percentText(long startedTick, long completionTick) {
        return Math.round(progressFraction(startedTick, completionTick) * 100f) + "%";
    }

    private float progressFraction(long startedTick, long completionTick) {
        if (completionTick <= startedTick) return 1f;
        return clamp01((simulationTime.tick() - startedTick) / (float) (completionTick - startedTick));
    }

    private static String humanize(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        int colon = value.lastIndexOf(':');
        String local = colon >= 0 && colon + 1 < value.length() ? value.substring(colon + 1) : value;
        local = local.replace('_', ' ').replace('-', ' ');
        if (local.isEmpty()) return value;
        return Character.toUpperCase(local.charAt(0)) + local.substring(1);
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

    private void clearTabBounds() {
        objectTabBounds = TabBounds.NONE;
        terrainTabBounds = TabBounds.NONE;
    }

    private static float fraction(long amount, long capacity) {
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

    private record TabBounds(float x, float y, float width, float height) {
        private static final TabBounds NONE = new TabBounds(0f, 0f, 0f, 0f);

        private boolean contains(float px, float py) {
            return width > 0f && height > 0f
                    && px >= x && px <= x + width
                    && py >= y && py <= y + height;
        }
    }
}
