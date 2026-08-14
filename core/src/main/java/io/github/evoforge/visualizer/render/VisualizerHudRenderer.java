package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.decision.AgentCandidateTrace;
import io.github.evoforge.simulation.world.agent.decision.AgentDecisionTrace;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.perception.vision.VisionSnapshot;
import io.github.evoforge.simulation.world.agent.search.AgentSearchTrace;
import io.github.evoforge.simulation.world.mechanics.growth.GrowthTrace;
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

/** Screen-space status and authoritative living-world inspector. */
public final class VisualizerHudRenderer {
    private static final int INSPECT_EXPOSURE_DISTANCE = 12;
    private static final Color PANEL = new Color(0.035f, 0.045f, 0.052f, 0.96f);
    private static final Color PANEL_BORDER = new Color(0.24f, 0.30f, 0.31f, 1f);
    private static final Color ACCENT = new Color(0.74f, 0.90f, 0.73f, 1f);
    private static final Color MUTED = new Color(0.72f, 0.77f, 0.76f, 1f);

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
    }

    public void draw() {
        float margin = 12f;
        float statusWidth = Math.min(760f, width - margin * 2f);
        float statusHeight = 104f;
        float statusX = margin;
        float statusY = height - margin - statusHeight;

        VisualizerState.CellSelection selectedCell = state.selectedCell();
        ObjectId selectedObject = state.selectedObject();
        List<InspectorLine> inspectorLines = selectedCell == null
                ? List.of()
                : inspectorLines(selectedCell, selectedObject);
        float inspectorWidth = Math.min(470f, width - margin * 2f);
        float desiredInspectorHeight = 34f + inspectorLines.size() * 19f;
        float inspectorHeight = Math.min(Math.max(180f, desiredInspectorHeight), height - margin * 2f);
        float inspectorX = width - margin - inspectorWidth;
        float inspectorY = height - margin - inspectorHeight;

        if (selectedCell != null && inspectorX < statusX + statusWidth + margin) {
            inspectorX = margin;
            inspectorY = statusY - margin - inspectorHeight;
            if (inspectorY < margin) inspectorY = margin;
        }

        shapes.setProjectionMatrix(projection);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawPanel(statusX, statusY, statusWidth, statusHeight);
        if (selectedCell != null) drawPanel(inspectorX, inspectorY, inspectorWidth, inspectorHeight);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_BORDER);
        shapes.rect(statusX, statusY, statusWidth, statusHeight);
        if (selectedCell != null) shapes.rect(inspectorX, inspectorY, inspectorWidth, inspectorHeight);
        shapes.end();

        batch.setProjectionMatrix(projection);
        batch.begin();
        font.getData().setScale(1f);
        float textX = statusX + 12f;
        float top = statusY + statusHeight - 12f;
        font.setColor(Color.WHITE);
        font.draw(
                batch,
                "STATUS   tick " + simulationTime.tick()
                        + "   Z " + state.selectedZ()
                        + "   FPS " + Gdx.graphics.getFramesPerSecond()
                        + "   " + (time.running() ? "RUN x1" : "PAUSED"),
                textX,
                top);
        font.setColor(MUTED);
        font.draw(batch, "Space run/pause | N step | LMB inspect/cycle stack | PgUp/PgDn slice", textX, top - 22f);
        font.draw(batch, "WASD pan | wheel zoom " + camera.zoomLabel()
                + " | G grid " + gridLabel(), textX, top - 44f);
        font.draw(batch, "F2 transitions " + onOff(state.showTransitions())
                + " | F3 ramps " + onOff(state.showShapeDirections())
                + " | F4 lower " + state.lowerDepth()
                + " | F5 occupancy " + onOff(state.showOccupancy()), textX, top - 66f);

        if (selectedCell != null) {
            float lineY = inspectorY + inspectorHeight - 15f;
            for (InspectorLine line : inspectorLines) {
                if (lineY < inspectorY + 10f) break;
                font.setColor(line.accent ? ACCENT : (line.muted ? MUTED : Color.WHITE));
                font.draw(batch, line.text, inspectorX + 12f, lineY);
                lineY -= 19f;
            }
        }
        batch.end();
    }

    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    private List<InspectorLine> inspectorLines(
            VisualizerState.CellSelection cell,
            ObjectId selectedObject) {
        List<InspectorLine> lines = new ArrayList<>();
        LandscapeSliceResolver.Cell slice = sliceResolver.analyze(
                cell.x(), cell.x(), cell.y(), cell.y(), cell.z(), state.lowerDepth(), INSPECT_EXPOSURE_DISTANCE)
                .resolve(cell.x(), cell.y());
        int stackCount = view.cells().objectCount(cell.x(), cell.y(), cell.z());
        lines.add(accent("CELL  (" + cell.x() + ", " + cell.y() + ", " + cell.z() + ")"));
        lines.add(normal("slice: " + sliceLabel(slice)));
        lines.add(normal("shape: " + shapePresentations.debugLabel(slice.shape())
                + " | transitions " + Integer.bitCount(view.navigation().transitions(cell.x(), cell.y(), cell.z()))));
        lines.add(normal("occupancy: " + view.occupancy().state(cell.x(), cell.y(), cell.z())
                + " | object stack " + stackCount + (stackCount > 1 ? "  (LMB cycles)" : "")));

        if (selectedObject == null) return lines;
        WorldObject object = view.objects().get(selectedObject);
        if (object == null || !view.transforms().has(selectedObject)) return lines;

        ObjectPresentation presentation = objectPresentations.get(object.definitionId());
        lines.add(accent("OBJECT  " + (presentation == null ? selectedObject.toString() : presentation.displayName())));
        if (presentation != null && !presentation.description().isBlank()) {
            lines.add(muted(trim(presentation.description(), 62)));
        }
        lines.add(normal("id " + selectedObject + " | definition " + object.definitionId()));
        String facing = view.orientations().has(selectedObject)
                ? " | facing " + view.orientations().facing(selectedObject)
                : "";
        lines.add(normal("XYZ " + view.transforms().x(selectedObject) + ", "
                + view.transforms().y(selectedObject) + ", " + view.transforms().z(selectedObject) + facing));

        int needCount = view.needs().needCount(selectedObject);
        if (needCount > 0) {
            lines.add(accent("PHYSIOLOGY"));
            for (int index = 0; index < needCount; index++) {
                NeedId needId = view.needs().needAt(selectedObject, index);
                long level = view.needs().level(selectedObject, needId);
                long max = view.needs().maxLevel(selectedObject, needId);
                String next = view.needProgression().has(selectedObject, needId)
                        ? " | next t" + view.needProgression().nextEvaluationTick(selectedObject, needId)
                        : "";
                lines.add(normal(needId.value() + "  " + bar(level, max)
                        + " " + level + "/" + max + next));
            }
        }

        if (view.consumableStocks().has(selectedObject)) {
            long quantity = view.consumableStocks().quantity(selectedObject);
            long capacity = view.consumableStocks().capacity(selectedObject);
            lines.add(accent("RESOURCE"));
            lines.add(normal("stock  " + bar(quantity, capacity) + " " + quantity + "/" + capacity));
            if (view.growth().has(selectedObject)) {
                GrowthTrace growth = view.growth().lastEvaluation(selectedObject);
                String last = growth == null
                        ? "none yet"
                        : "t" + growth.tick() + " +" + growth.appliedAmount();
                lines.add(normal("growth next t" + view.growth().nextEvaluationTick(selectedObject)
                        + " | last " + last));
            }
        }

        VisionSnapshot vision = view.vision().snapshot(selectedObject);
        if (vision != null) {
            lines.add(accent("PERCEPTION"));
            lines.add(normal("vision range " + vision.range() + " | FOV " + vision.horizontalFovDegrees()
                    + " deg | visible objects " + vision.objects().size()));
        }

        AgentIntentTrace intent = view.agents().currentIntent(selectedObject);
        AgentDecisionTrace decision = view.agents().lastDecision(selectedObject);
        AgentSearchTrace search = view.searches().currentSearch(selectedObject);
        if (intent != null || decision != null || search != null) {
            lines.add(accent("AUTONOMY"));
            if (intent != null) {
                String timing = intent.expectedCompletionTick() < 0L
                        ? ""
                        : " | " + progress(intent.startedTick(), intent.expectedCompletionTick())
                                + " | completes t" + intent.expectedCompletionTick();
                lines.add(normal("intent " + intent.phase() + timing));
                if (intent.targetId() != null) {
                    lines.add(normal("target " + objectLabel(intent.targetId()) + " " + intent.targetId()));
                }
            }
            if (search != null) {
                lines.add(normal("search " + search.status() + " | " + search.motivation()
                        + " | views " + search.headingsObserved()));
            }
            if (decision != null) {
                lines.add(normal("decision t" + decision.tick() + " | candidates " + decision.candidates().size()));
                AgentCandidateTrace selected = decision.selected();
                if (selected != null) {
                    lines.add(normal("winner " + objectLabel(selected.sourceId()) + " | " + selected.motivation()
                            + " | score " + selected.score() + " | benefit " + selected.expectedBenefit()));
                }
                int shown = 0;
                for (AgentCandidateTrace candidate : decision.candidates()) {
                    if (shown++ >= 3) break;
                    lines.add(muted("  cand " + objectLabel(candidate.sourceId())
                            + " d" + candidate.distance() + " s" + candidate.score()));
                }
            }
        }
        return lines;
    }

    private String objectLabel(ObjectId objectId) {
        WorldObject object = view.objects().get(objectId);
        if (object == null) return "<gone>";
        ObjectPresentation presentation = objectPresentations.get(object.definitionId());
        return presentation == null ? object.definitionId().toString() : presentation.displayName();
    }

    private String progress(long start, long end) {
        if (end <= start) return "100%";
        long elapsed = Math.max(0L, Math.min(end - start, simulationTime.tick() - start));
        return (elapsed * 100L / (end - start)) + "%";
    }

    private static String bar(long value, long max) {
        int cells = 10;
        int filled = max <= 0L ? 0 : (int) Math.max(0L, Math.min(cells, value * cells / max));
        return "[" + "#".repeat(filled) + ".".repeat(cells - filled) + "]";
    }

    private static String sliceLabel(LandscapeSliceResolver.Cell cell) {
        return switch (cell.kind()) {
            case SOLID_BODY -> "SOLID BODY z=" + cell.terrainZ();
            case CURRENT_SURFACE -> "SURFACE z=" + cell.terrainZ();
            case LOWER_SURFACE -> "LOWER -" + cell.dropDepth() + " z=" + cell.terrainZ();
            case EMPTY -> "EMPTY";
        };
    }

    private void drawPanel(float x, float y, float width, float height) {
        shapes.setColor(PANEL);
        shapes.rect(x, y, width, height);
    }

    private String gridLabel() {
        return switch (state.gridMode()) {
            case 0 -> "OFF";
            case 1 -> "SUBTLE";
            default -> "DEBUG";
        };
    }

    private static String onOff(boolean value) { return value ? "ON" : "OFF"; }
    private static String trim(String text, int max) { return text.length() <= max ? text : text.substring(0, max - 1) + "…"; }
    private static InspectorLine accent(String text) { return new InspectorLine(text, true, false); }
    private static InspectorLine normal(String text) { return new InspectorLine(text, false, false); }
    private static InspectorLine muted(String text) { return new InspectorLine(text, false, true); }

    private static <T> T require(T value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " must not be null");
        return value;
    }

    private record InspectorLine(String text, boolean accent, boolean muted) { }
}
