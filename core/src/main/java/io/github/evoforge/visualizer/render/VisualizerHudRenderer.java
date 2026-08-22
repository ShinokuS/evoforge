package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Align;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.agents.decision.AgentCandidateTrace;
import io.github.evoforge.simulation.agents.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.agents.decision.AgentIntentPhase;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.agents.need.NeedId;
import io.github.evoforge.simulation.agents.perception.vision.VisionSnapshot;
import io.github.evoforge.simulation.agents.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.object.stock.growth.GrowthStatus;
import io.github.evoforge.simulation.world.object.stock.growth.GrowthTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerTimeController;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import java.util.ArrayList;
import java.util.List;

/** Screen-space runtime status and living-world inspector. */
public final class VisualizerHudRenderer {
    private static final int INSPECT_EXPOSURE_DISTANCE = 12;
    private static final float PANEL_MARGIN = 12f;
    private static final float PANEL_PADDING = 16f;
    private static final float ROW_GAP = 6f;
    private static final float COLUMN_GAP = 12f;
    private static final float BAR_HEIGHT = 11f;

    private static final Color PANEL = new Color(0.030f, 0.040f, 0.047f, 0.97f);
    private static final Color PANEL_BORDER = new Color(0.28f, 0.35f, 0.35f, 1f);
    private static final Color TITLE = new Color(0.89f, 0.98f, 0.87f, 1f);
    private static final Color SECTION = new Color(0.67f, 0.87f, 0.69f, 1f);
    private static final Color TEXT = new Color(0.96f, 0.98f, 0.97f, 1f);
    private static final Color MUTED = new Color(0.73f, 0.79f, 0.77f, 1f);
    private static final Color BAR_BACKGROUND = new Color(0.13f, 0.17f, 0.17f, 1f);
    private static final Color NEED_FILL = new Color(0.90f, 0.57f, 0.22f, 1f);
    private static final Color RESOURCE_FILL = new Color(0.33f, 0.72f, 0.35f, 1f);
    private static final Color ACTION_FILL = new Color(0.34f, 0.67f, 0.89f, 1f);

    private final SimulationView view;
    private final SimulationTime simulationTime;
    private final VisualizerTimeController time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final LandscapeSliceResolver sliceResolver;
    private final ShapePresentationRegistry shapePresentations;
    private final ObjectPresentationBindings objectPresentations;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private final GlyphLayout glyphLayout = new GlyphLayout();
    private final Matrix4 projection = new Matrix4();

    private int width = 1;
    private int height = 1;
    private long cachedInspectorTick = Long.MIN_VALUE;
    private VisualizerState.CellSelection cachedInspectorCell;
    private ObjectId cachedInspectorObject;
    private int cachedInspectorLowerDepth = Integer.MIN_VALUE;
    private boolean cachedTechnicalDetails;
    private List<InspectorRow> cachedInspectorRows = List.of();
    private List<InspectorRow> cachedLayoutSource = List.of();
    private float cachedLayoutWidth = Float.NaN;
    private List<LayoutRow> cachedLayoutRows = List.of();

    public VisualizerHudRenderer(
            SimulationView view,
            SimulationTime simulationTime,
            VisualizerTimeController time,
            VisualizerState state,
            VisualizerCamera camera,
            LandscapeSliceResolver sliceResolver,
            ShapePresentationRegistry shapePresentations,
            ObjectPresentationBindings objectPresentations) {
        this.view = require(view, "view");
        this.simulationTime = require(simulationTime, "simulationTime");
        this.time = require(time, "time");
        this.state = require(state, "state");
        this.camera = require(camera, "camera");
        this.sliceResolver = require(sliceResolver, "sliceResolver");
        this.shapePresentations = require(shapePresentations, "shapePresentations");
        this.objectPresentations = require(objectPresentations, "objectPresentations");
    }

    public void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        this.width = width;
        this.height = height;
        projection.setToOrtho2D(0f, 0f, width, height);
        cachedLayoutWidth = Float.NaN;
    }

    public void draw() {
        float availableWidth = Math.max(1f, width - PANEL_MARGIN * 2f);
        float statusWidth = Math.min(520f, availableWidth);
        String statusLine = (time.running() ? "RUNNING" : "PAUSED")
                + "   Tick " + simulationTime.tick()
                + "   FPS " + Gdx.graphics.getFramesPerSecond()
                + "   Zoom " + camera.zoomLabel();
        float statusContentWidth = Math.max(1f, statusWidth - PANEL_PADDING * 2f);
        float statusLineHeight = measureWrapped(statusLine, statusContentWidth, 1.18f);
        float statusHeight = PANEL_PADDING * 2f + statusLineHeight;
        float statusX = PANEL_MARGIN;
        float statusY = height - PANEL_MARGIN - statusHeight;

        VisualizerState.CellSelection selectedCell = state.selectedCell();
        ObjectId selectedObject = state.selectedObject();
        float inspectorWidth = Math.min(470f, availableWidth);
        List<InspectorRow> rows = selectedCell == null
                ? List.of()
                : cachedInspectorRows(selectedCell, selectedObject);
        List<LayoutRow> layoutRows = selectedCell == null
                ? List.of()
                : cachedLayoutRows(rows, inspectorWidth);

        float inspectorHeight = Math.min(
                Math.max(146f, PANEL_PADDING * 2f + rowsHeight(layoutRows)),
                Math.max(1f, height - PANEL_MARGIN * 2f));
        float inspectorX = width - PANEL_MARGIN - inspectorWidth;
        float inspectorY = height - PANEL_MARGIN - inspectorHeight;

        if (selectedCell != null && inspectorX < statusX + statusWidth + PANEL_MARGIN) {
            inspectorX = PANEL_MARGIN;
            inspectorY = Math.max(PANEL_MARGIN, statusY - PANEL_MARGIN - inspectorHeight);
        }

        drawBackgrounds(
                statusX,
                statusY,
                statusWidth,
                statusHeight,
                selectedCell != null,
                inspectorX,
                inspectorY,
                inspectorWidth,
                inspectorHeight,
                layoutRows);
        drawText(
                statusX,
                statusY,
                statusHeight,
                statusWidth,
                statusLine,
                selectedCell != null,
                inspectorX,
                inspectorY,
                inspectorWidth,
                inspectorHeight,
                layoutRows);
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    private void drawBackgrounds(
            float statusX,
            float statusY,
            float statusWidth,
            float statusHeight,
            boolean hasInspector,
            float inspectorX,
            float inspectorY,
            float inspectorWidth,
            float inspectorHeight,
            List<LayoutRow> rows) {
        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanel(statusX, statusY, statusWidth, statusHeight);
        if (hasInspector) {
            drawPanel(inspectorX, inspectorY, inspectorWidth, inspectorHeight);
            float rowTop = inspectorY + inspectorHeight - PANEL_PADDING;
            for (LayoutRow layout : rows) {
                InspectorRow row = layout.row();
                if (row.kind() == RowKind.BAR) {
                    float barX = inspectorX + PANEL_PADDING;
                    float barY = rowTop - layout.height() + 4f;
                    float barWidth = Math.max(1f, inspectorWidth - PANEL_PADDING * 2f);
                    shapes.setColor(BAR_BACKGROUND);
                    shapes.rect(barX, barY, barWidth, BAR_HEIGHT);
                    shapes.setColor(row.barColor());
                    shapes.rect(barX, barY, barWidth * clamp01(row.fraction()), BAR_HEIGHT);
                }
                rowTop -= layout.height() + ROW_GAP;
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_BORDER);
        shapes.rect(statusX, statusY, statusWidth, statusHeight);
        if (hasInspector) shapes.rect(inspectorX, inspectorY, inspectorWidth, inspectorHeight);
        shapes.end();
    }

    private void drawText(
            float statusX,
            float statusY,
            float statusHeight,
            float statusWidth,
            String statusLine,
            boolean hasInspector,
            float inspectorX,
            float inspectorY,
            float inspectorWidth,
            float inspectorHeight,
            List<LayoutRow> rows) {
        batch.setProjectionMatrix(projection);
        batch.begin();

        float statusContentWidth = Math.max(1f, statusWidth - PANEL_PADDING * 2f);
        float statusTop = statusY + statusHeight - PANEL_PADDING;
        font.getData().setScale(1.18f);
        font.setColor(TEXT);
        font.draw(batch, statusLine, statusX + PANEL_PADDING, statusTop,
                statusContentWidth, Align.left, true);

        if (hasInspector) {
            float contentWidth = Math.max(1f, inspectorWidth - PANEL_PADDING * 2f);
            float labelWidth = labelColumnWidth(contentWidth);
            float valueWidth = Math.max(1f, contentWidth - labelWidth - COLUMN_GAP);
            float valueX = inspectorX + PANEL_PADDING + labelWidth + COLUMN_GAP;
            float rowTop = inspectorY + inspectorHeight - PANEL_PADDING;

            for (LayoutRow layout : rows) {
                if (rowTop < inspectorY + 8f) break;
                InspectorRow row = layout.row();
                switch (row.kind()) {
                    case TITLE -> {
                        font.setColor(TITLE);
                        font.getData().setScale(1.27f);
                        font.draw(batch, row.label(), inspectorX + PANEL_PADDING, rowTop,
                                contentWidth, Align.left, true);
                    }
                    case SECTION -> {
                        font.setColor(SECTION);
                        font.getData().setScale(1.08f);
                        font.draw(batch, row.label(), inspectorX + PANEL_PADDING, rowTop,
                                contentWidth, Align.left, true);
                    }
                    case TEXT -> {
                        font.getData().setScale(1.04f);
                        font.setColor(row.muted() ? MUTED : TEXT);
                        if (row.value() == null || row.value().isBlank()) {
                            font.draw(batch, row.label(), inspectorX + PANEL_PADDING, rowTop,
                                    contentWidth, Align.left, true);
                        } else {
                            font.draw(batch, row.label(), inspectorX + PANEL_PADDING, rowTop,
                                    labelWidth, Align.left, true);
                            font.draw(batch, row.value(), valueX, rowTop,
                                    valueWidth, Align.left, true);
                        }
                    }
                    case BAR -> {
                        font.getData().setScale(1.04f);
                        float valueColumn = Math.min(98f, Math.max(60f, contentWidth * 0.26f));
                        float barLabelWidth = Math.max(1f, contentWidth - valueColumn - COLUMN_GAP);
                        font.setColor(TEXT);
                        font.draw(batch, row.label(), inspectorX + PANEL_PADDING, rowTop,
                                barLabelWidth, Align.left, true);
                        if (row.value() != null) {
                            font.setColor(MUTED);
                            font.draw(batch, row.value(),
                                    inspectorX + PANEL_PADDING + barLabelWidth + COLUMN_GAP,
                                    rowTop,
                                    valueColumn,
                                    Align.right,
                                    false);
                        }
                    }
                    case SPACER -> { }
                }
                rowTop -= layout.height() + ROW_GAP;
            }
        }
        batch.end();
        font.getData().setScale(1f);
    }

    private List<InspectorRow> cachedInspectorRows(
            VisualizerState.CellSelection cell,
            ObjectId selectedObject) {
        long tick = simulationTime.tick();
        int lowerDepth = state.lowerDepth();
        boolean technical = state.showTechnicalDetails();
        if (tick != cachedInspectorTick
                || !sameCell(cell, cachedInspectorCell)
                || !sameObject(selectedObject, cachedInspectorObject)
                || lowerDepth != cachedInspectorLowerDepth
                || technical != cachedTechnicalDetails) {
            cachedInspectorTick = tick;
            cachedInspectorCell = cell;
            cachedInspectorObject = selectedObject;
            cachedInspectorLowerDepth = lowerDepth;
            cachedTechnicalDetails = technical;
            cachedInspectorRows = List.copyOf(inspectorRows(cell, selectedObject, technical));
            cachedLayoutSource = List.of();
        }
        return cachedInspectorRows;
    }

    private List<LayoutRow> cachedLayoutRows(List<InspectorRow> rows, float inspectorWidth) {
        float contentWidth = Math.max(1f, inspectorWidth - PANEL_PADDING * 2f);
        if (rows != cachedLayoutSource || Float.compare(contentWidth, cachedLayoutWidth) != 0) {
            cachedLayoutSource = rows;
            cachedLayoutWidth = contentWidth;
            List<LayoutRow> result = new ArrayList<>(rows.size());
            for (InspectorRow row : rows) {
                result.add(new LayoutRow(row, measuredRowHeight(row, contentWidth)));
            }
            cachedLayoutRows = List.copyOf(result);
        }
        return cachedLayoutRows;
    }

    private float measuredRowHeight(InspectorRow row, float contentWidth) {
        return switch (row.kind()) {
            case TITLE -> Math.max(29f, measureWrapped(row.label(), contentWidth, 1.27f));
            case SECTION -> Math.max(22f, measureWrapped(row.label(), contentWidth, 1.08f));
            case TEXT -> {
                if (row.value() == null || row.value().isBlank()) {
                    yield Math.max(20f, measureWrapped(row.label(), contentWidth, 1.04f));
                }
                float labelWidth = labelColumnWidth(contentWidth);
                float valueWidth = Math.max(1f, contentWidth - labelWidth - COLUMN_GAP);
                yield Math.max(
                        20f,
                        Math.max(
                                measureWrapped(row.label(), labelWidth, 1.04f),
                                measureWrapped(row.value(), valueWidth, 1.04f)));
            }
            case BAR -> 41f;
            case SPACER -> 7f;
        };
    }

    private float measureWrapped(String text, float targetWidth, float scale) {
        font.getData().setScale(scale);
        glyphLayout.setText(
                font,
                text == null ? "" : text,
                Color.WHITE,
                Math.max(1f, targetWidth),
                Align.left,
                true);
        float measured = Math.max(font.getLineHeight(), glyphLayout.height);
        font.getData().setScale(1f);
        return measured;
    }

    private static float labelColumnWidth(float contentWidth) {
        return Math.min(142f, Math.max(92f, contentWidth * 0.34f));
    }

    private List<InspectorRow> inspectorRows(
            VisualizerState.CellSelection cell,
            ObjectId selectedObject,
            boolean technical) {
        List<InspectorRow> rows = new ArrayList<>();
        int stackCount = view.cells().objectCount(cell.x(), cell.y(), cell.z());

        if (selectedObject == null) {
            rows.add(title("Cell " + cell.x() + ", " + cell.y()));
            rows.add(text("Objects here", Integer.toString(stackCount)));
            if (technical) appendCellTechnical(rows, cell, stackCount);
            return rows;
        }

        WorldObject object = view.objects().get(selectedObject);
        if (object == null || !view.positions().has(selectedObject)) {
            rows.add(title("Object unavailable"));
            return rows;
        }

        ObjectPresentation presentation = objectPresentations.get(object.definitionId());
        rows.add(title(presentation == null ? "Object" : presentation.displayName()));
        if (stackCount > 1) {
            rows.add(muted("Several objects share this cell - click again to cycle", null));
        }

        appendBehavior(rows, selectedObject);
        appendNeeds(rows, selectedObject);
        appendResource(rows, selectedObject);

        if (technical) {
            rows.add(spacer());
            rows.add(section("TECHNICAL DETAILS"));
            appendCellTechnical(rows, cell, stackCount);
            rows.add(text("Object id", selectedObject.toString()));
            rows.add(text("Definition", object.definitionId().toString()));
            String facing = view.orientations().has(selectedObject)
                    ? view.orientations().facing(selectedObject).toString()
                    : "n/a";
            rows.add(text("Position", view.positions().x(selectedObject) + ", "
                    + view.positions().y(selectedObject) + ", " + view.positions().z(selectedObject)));
            rows.add(text("Facing", facing));
            appendTechnicalAutonomy(rows, selectedObject);
        }
        return rows;
    }

    private void appendBehavior(List<InspectorRow> rows, ObjectId objectId) {
        AgentIntentTrace intent = view.agents().currentIntent(objectId);
        AgentSearchTrace search = view.searches().currentSearch(objectId);
        AgentDecisionTrace decision = view.agents().lastDecision(objectId);
        if (intent == null && search == null && decision == null) return;

        rows.add(spacer());
        rows.add(section("BEHAVIOR"));

        String motivation = currentMotivation(search, decision);
        if (intent != null) {
            String target = targetLabel(intent.targetKey());
            rows.add(text("Activity", readableActivity(intent, search, motivation, target)));
            if (target != null) rows.add(text("Target", target));
            if (intent.phase() == AgentIntentPhase.USING_OPPORTUNITY
                    && intent.expectedCompletionTick() > intent.startedTick()) {
                rows.add(bar(
                        "Current interaction",
                        percentText(intent.startedTick(), intent.expectedCompletionTick()),
                        progressFraction(intent.startedTick(), intent.expectedCompletionTick()),
                        ACTION_FILL));
            }
        } else if (search != null) {
            rows.add(text("Activity", readableSearch(search, motivation)));
        } else {
            rows.add(text("Activity", "Idle / deciding"));
        }
        if (motivation != null) rows.add(text("Current need", humanizeId(motivation)));
    }

    private void appendNeeds(List<InspectorRow> rows, ObjectId objectId) {
        int count = view.needs().needCount(objectId);
        if (count <= 0) return;
        rows.add(spacer());
        rows.add(section("NEEDS"));
        for (int index = 0; index < count; index++) {
            NeedId needId = view.needs().needAt(objectId, index);
            long level = view.needs().level(objectId, needId);
            long max = view.needs().maxLevel(objectId, needId);
            rows.add(bar(
                    humanizeId(needId.value()),
                    level + " / " + max,
                    fraction(level, max),
                    NEED_FILL));
        }
    }

    private void appendResource(List<InspectorRow> rows, ObjectId objectId) {
        if (!view.consumableStocks().has(objectId)) return;
        long quantity = view.consumableStocks().quantity(objectId);
        long capacity = view.consumableStocks().capacity(objectId);
        rows.add(spacer());
        rows.add(section("RESOURCE"));
        rows.add(bar("Biomass", quantity + " / " + capacity, fraction(quantity, capacity), RESOURCE_FILL));

        if (view.growth().has(objectId)) {
            GrowthStatus status = view.growth().status(objectId);
            if (status == GrowthStatus.DORMANT_FULL) {
                rows.add(text("Growth", "Full grown - dormant"));
            } else {
                long next = view.growth().nextEvaluationTick(objectId);
                long remaining = Math.max(0L, next - simulationTime.tick());
                rows.add(text("Growth", "Regrowing - next in " + remaining + " ticks"));
            }
        }
    }

    private void appendCellTechnical(
            List<InspectorRow> rows,
            VisualizerState.CellSelection cell,
            int stackCount) {
        LandscapeSliceResolver.Cell slice = sliceResolver.analyze(
                cell.x(), cell.x(), cell.y(), cell.y(), cell.z(), state.lowerDepth(), INSPECT_EXPOSURE_DISTANCE)
                .resolve(cell.x(), cell.y());
        rows.add(text("Cell", cell.x() + ", " + cell.y() + ", " + cell.z()));
        rows.add(text("Slice", sliceLabel(slice)));
        rows.add(text("Shape", shapePresentations.debugLabel(slice.shape())));
        rows.add(text("Transitions", Integer.toString(Integer.bitCount(
                view.navigation().transitions(cell.x(), cell.y(), cell.z())))));
        rows.add(text("Occupancy", view.occupancy().state(cell.x(), cell.y(), cell.z()).toString()));
        rows.add(text("Object stack", Integer.toString(stackCount)));
    }

    private void appendTechnicalAutonomy(List<InspectorRow> rows, ObjectId objectId) {
        for (int index = 0; index < view.needs().needCount(objectId); index++) {
            NeedId needId = view.needs().needAt(objectId, index);
            if (view.needProgression().has(objectId, needId)) {
                rows.add(text(humanizeId(needId.value()) + " progression",
                        "next t" + view.needProgression().nextEvaluationTick(objectId, needId)));
            }
        }

        VisionSnapshot vision = view.vision().snapshot(objectId);
        if (vision != null) {
            rows.add(text("Vision", "range " + vision.range()
                    + " - FOV " + vision.horizontalFovDegrees()
                    + " deg - cells " + vision.cells().size()
                    + " - objects " + vision.objects().size()));
        }

        AgentSearchTrace search = view.searches().currentSearch(objectId);
        if (search != null) {
            rows.add(text("Search raw", search.status() + " - headings " + search.headingsObserved()));
        }

        AgentDecisionTrace decision = view.agents().lastDecision(objectId);
        if (decision != null) {
            rows.add(text("Decision", "t" + decision.tick() + " - candidates " + decision.candidates().size()));
            AgentCandidateTrace selected = decision.selected();
            if (selected != null) {
                rows.add(text("Winner", targetLabel(selected.targetKey())
                        + " - utility " + selected.utility()
                        + " - benefit " + selected.expectedBenefit()));
            }
        }

        if (view.growth().has(objectId)) {
            GrowthTrace growth = view.growth().lastEvaluation(objectId);
            String last = growth == null
                    ? "none"
                    : "t" + growth.tick() + " - resolved " + growth.resolvedAmount()
                            + " - applied " + growth.appliedAmount();
            rows.add(text("Growth raw", last));
        }
    }

    private String readableActivity(
            AgentIntentTrace intent,
            AgentSearchTrace search,
            String motivation,
            String target) {
        return switch (intent.phase()) {
            case MOVING_TO_OPPORTUNITY -> target == null ? "Moving" : "Moving to " + target;
            case USING_OPPORTUNITY -> motivation == null
                    ? (target == null ? "Interacting" : "Interacting with " + target)
                    : "Satisfying " + humanizeId(motivation)
                            + (target == null ? "" : " at " + target);
            case SEARCH_RELOCATION -> readableSearch(search, motivation);
        };
    }

    private static String readableSearch(AgentSearchTrace search, String motivation) {
        String goal = motivation == null ? "a solution" : humanizeId(motivation) + " solution";
        if (search == null) return "Exploring for " + goal;
        return switch (search.status()) {
            case SWEEPING -> "Looking around for " + goal;
            case EXPLORING -> "Exploring for " + goal;
            case RELOCATION_BLOCKED -> "Search route blocked - reconsidering";
        };
    }

    private static String currentMotivation(AgentSearchTrace search, AgentDecisionTrace decision) {
        if (search != null && search.motivation() != null && !search.motivation().isBlank()) {
            return search.motivation();
        }
        if (decision != null && decision.selected() != null) {
            return decision.selected().motivation();
        }
        return null;
    }

    private String targetLabel(String targetKey) {
        if (targetKey == null || targetKey.isBlank()) return null;
        if (targetKey.startsWith("object:")) {
            try {
                long value = Long.parseLong(targetKey.substring("object:".length()));
                ObjectId objectId = ObjectId.of(
                        (int) (value & 0xFFFF_FFFFL),
                        (int) (value >>> 32));
                return objectLabel(objectId);
            } catch (IllegalArgumentException ignored) {
                return targetKey;
            }
        }
        if (targetKey.startsWith("liquid:")) {
            int at = targetKey.indexOf('@');
            int hash = targetKey.lastIndexOf('#');
            if (at >= 0 && hash > at) {
                return "Water " + targetKey.substring(at + 1, hash);
            }
            return "Water";
        }
        return targetKey;
    }

    private String objectLabel(ObjectId objectId) {
        WorldObject object = view.objects().get(objectId);
        if (object == null) return "Unavailable";
        ObjectPresentation presentation = objectPresentations.get(object.definitionId());
        return presentation == null ? "Object" : presentation.displayName();
    }

    private float progressFraction(long start, long end) {
        if (end <= start) return 1f;
        long elapsed = Math.max(0L, Math.min(end - start, simulationTime.tick() - start));
        return (float) elapsed / (float) (end - start);
    }

    private String percentText(long start, long end) {
        return Math.round(progressFraction(start, end) * 100f) + "%";
    }

    private static float fraction(long value, long max) {
        if (max <= 0L) return 0f;
        return clamp01((float) value / (float) max);
    }

    private static float rowsHeight(List<LayoutRow> rows) {
        float total = 0f;
        for (LayoutRow row : rows) total += row.height() + ROW_GAP;
        return Math.max(0f, total - ROW_GAP);
    }

    private static String humanizeId(String value) {
        if (value == null || value.isBlank()) return "Unknown";
        int colon = value.lastIndexOf(':');
        String raw = colon >= 0 ? value.substring(colon + 1) : value;
        raw = raw.replace('_', ' ').replace('-', ' ').replace('.', ' ');
        StringBuilder result = new StringBuilder(raw.length());
        boolean upper = true;
        for (int index = 0; index < raw.length(); index++) {
            char c = raw.charAt(index);
            if (Character.isWhitespace(c)) {
                upper = true;
                result.append(c);
            } else if (upper) {
                result.append(Character.toUpperCase(c));
                upper = false;
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    private static boolean sameCell(
            VisualizerState.CellSelection left,
            VisualizerState.CellSelection right) {
        if (left == right) return true;
        return left != null && right != null
                && left.x() == right.x()
                && left.y() == right.y()
                && left.z() == right.z();
    }

    private static boolean sameObject(ObjectId left, ObjectId right) {
        return left == right || (left != null && left.equals(right));
    }

    private static String sliceLabel(LandscapeSliceResolver.Cell cell) {
        return switch (cell.kind()) {
            case SOLID_BODY -> "solid body z=" + cell.terrainZ();
            case CURRENT_SURFACE -> "surface z=" + cell.terrainZ();
            case LOWER_SURFACE -> "lower -" + cell.dropDepth() + " z=" + cell.terrainZ();
            case EMPTY -> "empty";
        };
    }

    private void drawPanel(float x, float y, float width, float height) {
        shapes.setColor(PANEL);
        shapes.rect(x, y, width, height);
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static InspectorRow title(String label) { return new InspectorRow(RowKind.TITLE, label, null, -1f, TITLE, false); }
    private static InspectorRow section(String label) { return new InspectorRow(RowKind.SECTION, label, null, -1f, SECTION, false); }
    private static InspectorRow text(String label, String value) { return new InspectorRow(RowKind.TEXT, label, value, -1f, TEXT, false); }
    private static InspectorRow muted(String label, String value) { return new InspectorRow(RowKind.TEXT, label, value, -1f, MUTED, true); }
    private static InspectorRow bar(String label, String value, float fraction, Color color) {
        return new InspectorRow(RowKind.BAR, label, value, fraction, color, false);
    }
    private static InspectorRow spacer() { return new InspectorRow(RowKind.SPACER, "", null, -1f, TEXT, false); }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    private enum RowKind { TITLE, SECTION, TEXT, BAR, SPACER }

    private record InspectorRow(
            RowKind kind,
            String label,
            String value,
            float fraction,
            Color barColor,
            boolean muted) { }

    private record LayoutRow(InspectorRow row, float height) { }
}
