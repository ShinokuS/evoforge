package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.hydrology.DrainageBasin;
import io.github.evoforge.simulation.world.atlas.hydrology.DrainageBasinTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.InlandLake;
import io.github.evoforge.simulation.world.atlas.hydrology.InlandLakeTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterBody;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterBoundaryRoute;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterDomainRole;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterHydrologyTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterRimCell;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterSpillConnection;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.WorldHydrologyTopology;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/** Presentation-only F4 overlay for inspecting Stage 2B drainage and standing-water facts. */
final class WorldGenerationHydrologyDiagnosticRenderer implements Disposable {
    private static final float FIT_PADDING = 1.08f;
    private static final float MIN_CAMERA_MARGIN = 2f;
    private static final float CAMERA_MARGIN_FRACTION = 0.03f;
    private static final float LABEL_SCALE = 0.85f;
    private static final float LABEL_OFFSET_PX = 6f;
    private static final float LEGEND_LINE_HEIGHT_PX = 16f;
    private static final float LEGEND_TOP_OFFSET_PX = 220f;

    private static final Color BASIN = new Color(0.98f, 0.68f, 0.12f, 0.18f);
    private static final Color GENERATED_LAKE = new Color(0.08f, 0.72f, 1f, 0.50f);
    private static final Color MICRO_WATER = new Color(0.82f, 0.82f, 0.86f, 0.62f);
    private static final Color OCEANIC_WATER = new Color(0.10f, 0.46f, 0.92f, 0.20f);
    private static final Color ROUTED_WATER = new Color(0.12f, 0.86f, 0.62f, 0.24f);
    private static final Color CLOSED_WATER = new Color(0.94f, 0.24f, 0.50f, 0.28f);
    private static final Color RIM = new Color(0.98f, 0.82f, 0.18f, 0.72f);
    private static final Color SPILL = new Color(0.96f, 0.56f, 0.12f, 0.55f);
    private static final Color ROUTE_GRAPH = new Color(0.22f, 0.94f, 0.96f, 0.62f);
    private static final Color SELECTED_ROUTE = new Color(0.38f, 1f, 0.86f, 0.96f);
    private static final Color LABEL = new Color(0.90f, 1f, 1f, 1f);

    private final VisualizerCamera camera = new VisualizerCamera();
    private final ShapeRenderer diagnostics = new ShapeRenderer(8_192);
    private final SpriteBatch labels = new SpriteBatch(256);
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 labelProjection = new Matrix4();

    private WorldBounds bounds;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
        camera.resize(width, height);
        labelProjection.setToOrtho2D(0f, 0f, width, height);
        fitToWorld();
    }

    void setWorldBounds(WorldBounds bounds) {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
        this.bounds = bounds;
        fitToWorld();
    }

    void update(float delta, boolean keyboardNavigation) {
        if (keyboardNavigation) {
            int x = 0;
            int y = 0;
            if (Gdx.input.isKeyPressed(Input.Keys.A)) x--;
            if (Gdx.input.isKeyPressed(Input.Keys.D)) x++;
            if (Gdx.input.isKeyPressed(Input.Keys.S)) y--;
            if (Gdx.input.isKeyPressed(Input.Keys.W)) y++;
            if (x != 0 || y != 0) camera.pan(x, y, delta);
        }
        constrainCamera();
    }

    void zoom(float amountY) {
        camera.zoom(amountY);
        constrainCamera();
    }

    void panByPixels(float deltaX, float deltaY) {
        float worldPerPixel = camera.worldUnitsPerPixel();
        camera.panBy(-deltaX * worldPerPixel, deltaY * worldPerPixel);
        constrainCamera();
    }

    void fitToWorld() {
        if (bounds == null || viewportWidth <= 0 || viewportHeight <= 0) return;
        camera.fitBounds(
                bounds.minX(),
                (float) bounds.maxX() + 1f,
                bounds.minY(),
                (float) bounds.maxY() + 1f,
                FIT_PADDING);
        constrainCamera();
    }

    void render(WorldHydrologyTopology topology) {
        if (topology == null || bounds == null) return;
        if (!bounds.equals(topology.bounds())) {
            throw new IllegalArgumentException("hydrology diagnostic topology must match preview bounds");
        }

        StandingWaterHydrologyTopology standingWater = topology.standingWaterTopology();
        constrainCamera();
        camera.update();
        VisualizerCamera.VisibleRange visible = clipped(camera.visibleRange());
        if (visible == null) return;

        Gdx.gl.glViewport(0, 0, viewportWidth, viewportHeight);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        diagnostics.setProjectionMatrix(camera.projection());
        diagnostics.begin(ShapeRenderer.ShapeType.Filled);

        drawDrainageBasins(topology.drainageBasins(), visible);
        drawGeneratedLakes(topology.inlandLakes(), visible);
        drawIgnoredMicroWater(standingWater, visible);
        drawBodies(standingWater, visible);
        drawInspectableRims(standingWater, visible);
        drawRouteGraph(standingWater);
        drawSpillMeetings(standingWater, visible);
        drawSelectedRoutes(standingWater, visible);

        diagnostics.end();
        drawLabels(topology);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    /** Compatibility path for focused standing-water diagnostics and older tests. */
    void render(StandingWaterHydrologyTopology topology) {
        if (topology == null || bounds == null) return;
        if (!bounds.equals(topology.bounds())) {
            throw new IllegalArgumentException("hydrology diagnostic topology must match preview bounds");
        }

        constrainCamera();
        camera.update();
        VisualizerCamera.VisibleRange visible = clipped(camera.visibleRange());
        if (visible == null) return;

        Gdx.gl.glViewport(0, 0, viewportWidth, viewportHeight);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        diagnostics.setProjectionMatrix(camera.projection());
        diagnostics.begin(ShapeRenderer.ShapeType.Filled);
        drawIgnoredMicroWater(topology, visible);
        drawBodies(topology, visible);
        drawInspectableRims(topology, visible);
        drawRouteGraph(topology);
        drawSpillMeetings(topology, visible);
        drawSelectedRoutes(topology, visible);
        diagnostics.end();
        drawLabels(topology);
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void dispose() {
        font.dispose();
        labels.dispose();
        diagnostics.dispose();
    }

    private void drawDrainageBasins(
            DrainageBasinTopology basins,
            VisualizerCamera.VisibleRange visible) {
        diagnostics.setColor(BASIN);
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                if (basins.basinIdAt(x, y) == DrainageBasinTopology.NO_BASIN) continue;
                diagnostics.rect(x + 0.05f, y + 0.05f, 0.90f, 0.90f);
            }
        }
    }

    private void drawGeneratedLakes(
            InlandLakeTopology lakes,
            VisualizerCamera.VisibleRange visible) {
        diagnostics.setColor(GENERATED_LAKE);
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                if (!lakes.isLakeAt(x, y)) continue;
                diagnostics.rect(x + 0.10f, y + 0.10f, 0.80f, 0.80f);
            }
        }
    }

    private void drawIgnoredMicroWater(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        StandingWaterTopology raw = topology.rawStandingWater();
        StandingWaterTopology selected = topology.standingWater();
        diagnostics.setColor(MICRO_WATER);
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                if (raw.bodyIdAt(x, y) == StandingWaterTopology.NO_BODY
                        || selected.bodyIdAt(x, y) != StandingWaterTopology.NO_BODY) {
                    continue;
                }
                diagnostics.rect(x + 0.16f, y + 0.16f, 0.68f, 0.68f);
            }
        }
    }

    private void drawBodies(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        StandingWaterTopology water = topology.standingWater();
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                int bodyId = water.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY) continue;
                diagnostics.setColor(bodyColor(topology, bodyId));
                diagnostics.rect(x + 0.08f, y + 0.08f, 0.84f, 0.84f);
            }
        }
    }

    private void drawInspectableRims(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        diagnostics.setColor(RIM);
        float marker = Math.max(0.12f, Math.min(0.30f, camera.worldUnitsPerPixel() * 3.2f));
        float half = marker * 0.5f;
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            if (topology.domains().isOceanic(bodyId)) continue;
            for (StandingWaterRimCell rim : topology.rims().rimCells(bodyId)) {
                if (!rim.hasDryContinuation() || !visibleContains(visible, rim.x(), rim.y())) continue;
                diagnostics.rect(rim.x() + 0.5f - half, rim.y() + 0.5f - half, marker, marker);
            }
        }
    }

    private void drawRouteGraph(StandingWaterHydrologyTopology topology) {
        float thickness = Math.max(0.045f, camera.worldUnitsPerPixel() * 2.1f);
        diagnostics.setColor(ROUTE_GRAPH);
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            StandingWaterBoundaryRoute route = topology.boundaryRoutes().route(bodyId);
            if (route.nextBodyId().isEmpty()) continue;
            StandingWaterBody from = topology.standingWater().body(bodyId);
            StandingWaterBody to = topology.standingWater().body(route.nextBodyId().getAsInt());
            drawDashedArrow(
                    bodyCenterX(from),
                    bodyCenterY(from),
                    bodyCenterX(to),
                    bodyCenterY(to),
                    thickness);
        }
    }

    private void drawDashedArrow(float x0, float y0, float x1, float y1, float thickness) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0.001f) return;
        float ux = dx / length;
        float uy = dy / length;
        float dash = Math.max(0.35f, camera.worldUnitsPerPixel() * 9f);
        float gap = dash * 0.72f;
        for (float start = 0f; start < length - dash; start += dash + gap) {
            float end = Math.min(start + dash, length);
            diagnostics.rectLine(
                    x0 + ux * start,
                    y0 + uy * start,
                    x0 + ux * end,
                    y0 + uy * end,
                    thickness);
        }
        float arrow = Math.max(0.28f, camera.worldUnitsPerPixel() * 8f);
        float baseX = x1 - ux * arrow;
        float baseY = y1 - uy * arrow;
        float sideX = -uy * arrow * 0.58f;
        float sideY = ux * arrow * 0.58f;
        diagnostics.triangle(
                x1,
                y1,
                baseX + sideX,
                baseY + sideY,
                baseX - sideX,
                baseY - sideY);
    }

    private void drawSpillMeetings(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        float thickness = Math.max(0.035f, camera.worldUnitsPerPixel() * 1.7f);
        diagnostics.setColor(SPILL);
        for (StandingWaterSpillConnection connection : topology.spills().connections()) {
            if (!edgeTouchesVisible(connection, visible)) continue;
            diagnostics.rectLine(
                    connection.meetingFirstX() + 0.5f,
                    connection.meetingFirstY() + 0.5f,
                    connection.meetingSecondX() + 0.5f,
                    connection.meetingSecondY() + 0.5f,
                    thickness);
        }
    }

    private void drawSelectedRoutes(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        float thickness = Math.max(0.07f, camera.worldUnitsPerPixel() * 3.4f);
        diagnostics.setColor(SELECTED_ROUTE);
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            StandingWaterBoundaryRoute route = topology.boundaryRoutes().route(bodyId);
            if (route.nextBodyId().isEmpty()) continue;
            StandingWaterSpillConnection selected = selectedConnection(
                    topology,
                    bodyId,
                    route.nextBodyId().getAsInt());
            if (selected == null || !edgeTouchesVisible(selected, visible)) continue;
            diagnostics.rectLine(
                    selected.meetingFirstX() + 0.5f,
                    selected.meetingFirstY() + 0.5f,
                    selected.meetingSecondX() + 0.5f,
                    selected.meetingSecondY() + 0.5f,
                    thickness);
        }
    }

    private void drawLabels(WorldHydrologyTopology topology) {
        StandingWaterHydrologyTopology standing = topology.standingWaterTopology();
        labels.setProjectionMatrix(labelProjection);
        labels.begin();
        font.getData().setScale(LABEL_SCALE);
        font.setColor(LABEL);

        drawStandingLabels(standing);
        for (int lakeId = 0; lakeId < topology.inlandLakes().lakeCount(); lakeId++) {
            InlandLake lake = topology.inlandLakes().lake(lakeId);
            VisualizerCamera.ScreenPoint point = camera.screenAt(
                    centerX(lake.minX(), lake.maxX()),
                    centerY(lake.minY(), lake.maxY()));
            if (!screenContains(point)) continue;
            font.draw(
                    labels,
                    String.format(
                            "L%d  %.1fZ  depth %.1fZ",
                            lake.id(),
                            lake.surfaceElevationSubunits() / (double) ElevationField.SUBUNITS_PER_CELL,
                            lake.maximumDepthSubunits() / (double) ElevationField.SUBUNITS_PER_CELL),
                    point.x() + LABEL_OFFSET_PX,
                    point.y() + LABEL_OFFSET_PX);
        }

        float legendX = 12f;
        float legendY = Math.max(68f, viewportHeight - LEGEND_TOP_OFFSET_PX);
        int ignored = standing.rawBodyCount() - standing.bodyCount();
        font.draw(labels, String.format(
                "F4 TOPOLOGY  basins %d  lakes %d  sea bodies %d  micro %d  oceanic %d",
                topology.drainageBasins().basinCount(),
                topology.inlandLakes().lakeCount(),
                standing.bodyCount(),
                ignored,
                standing.domains().oceanicBodyCount()),
                legendX,
                legendY);
        font.draw(labels,
                "AMBER terrain basin | BRIGHT BLUE generated inland lake | BLUE oceanic water",
                legendX,
                legendY - LEGEND_LINE_HEIGHT_PX);
        font.draw(labels,
                "CYAN dashed: sea-body route | ORANGE: spill candidate | YELLOW: old sea-level inland rim",
                legendX,
                legendY - LEGEND_LINE_HEIGHT_PX * 2f);
        labels.end();
    }

    private void drawLabels(StandingWaterHydrologyTopology topology) {
        labels.setProjectionMatrix(labelProjection);
        labels.begin();
        font.getData().setScale(LABEL_SCALE);
        font.setColor(LABEL);
        drawStandingLabels(topology);

        float legendX = 12f;
        float legendY = Math.max(52f, viewportHeight - LEGEND_TOP_OFFSET_PX);
        int ignored = topology.rawBodyCount() - topology.bodyCount();
        font.draw(labels, String.format(
                "F4 TOPOLOGY  raw %d  hydrologic %d  micro %d  oceanic %d  inland %d",
                topology.rawBodyCount(),
                topology.bodyCount(),
                ignored,
                topology.domains().oceanicBodyCount(),
                topology.domains().inlandBodyCount()),
                legendX,
                legendY);
        labels.end();
    }

    private void drawStandingLabels(StandingWaterHydrologyTopology topology) {
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            StandingWaterBody body = topology.standingWater().body(bodyId);
            StandingWaterBoundaryRoute route = topology.boundaryRoutes().route(bodyId);
            VisualizerCamera.ScreenPoint point = camera.screenAt(
                    bodyCenterX(body),
                    bodyCenterY(body));
            if (!screenContains(point)) continue;
            font.draw(
                    labels,
                    bodyLabel(topology, route),
                    point.x() + LABEL_OFFSET_PX,
                    point.y() + LABEL_OFFSET_PX);
        }
    }

    private boolean screenContains(VisualizerCamera.ScreenPoint point) {
        return point.x() >= 0f && point.x() <= viewportWidth
                && point.y() >= 0f && point.y() <= viewportHeight;
    }

    private static String bodyLabel(
            StandingWaterHydrologyTopology topology,
            StandingWaterBoundaryRoute route) {
        if (topology.domains().role(route.bodyId()) == StandingWaterDomainRole.OCEANIC) {
            return "#" + route.bodyId() + " OCEANIC";
        }
        if (route.nextBodyId().isEmpty()) return "#" + route.bodyId() + " SEA-LEVEL CLOSED";
        double barrier = route.minimumBarrierElevationSubunits().getAsLong()
                / (double) ElevationField.SUBUNITS_PER_CELL;
        return String.format(
                "#%d SEA-LEVEL -> #%d  barrier %.1fZ",
                route.bodyId(),
                route.nextBodyId().getAsInt(),
                barrier);
    }

    private static float bodyCenterX(StandingWaterBody body) {
        return centerX(body.minX(), body.maxX());
    }

    private static float bodyCenterY(StandingWaterBody body) {
        return centerY(body.minY(), body.maxY());
    }

    private static float centerX(int minX, int maxX) {
        return (minX + maxX + 1f) * 0.5f;
    }

    private static float centerY(int minY, int maxY) {
        return (minY + maxY + 1f) * 0.5f;
    }

    private static StandingWaterSpillConnection selectedConnection(
            StandingWaterHydrologyTopology topology,
            int bodyId,
            int nextBodyId) {
        StandingWaterSpillConnection best = null;
        for (StandingWaterSpillConnection connection : topology.spills().connectionsForBody(bodyId)) {
            if (connection.otherBodyId(bodyId) != nextBodyId) continue;
            if (best == null
                    || connection.barrierElevationSubunits() < best.barrierElevationSubunits()) {
                best = connection;
            }
        }
        return best;
    }

    private static Color bodyColor(StandingWaterHydrologyTopology topology, int bodyId) {
        if (topology.domains().isOceanic(bodyId)) return OCEANIC_WATER;
        return topology.boundaryRoutes().route(bodyId).reachesExternalSink()
                ? ROUTED_WATER
                : CLOSED_WATER;
    }

    private static boolean edgeTouchesVisible(
            StandingWaterSpillConnection connection,
            VisualizerCamera.VisibleRange visible) {
        return visibleContains(visible, connection.meetingFirstX(), connection.meetingFirstY())
                || visibleContains(visible, connection.meetingSecondX(), connection.meetingSecondY());
    }

    private static boolean visibleContains(
            VisualizerCamera.VisibleRange visible,
            int x,
            int y) {
        return x >= visible.minX() && x <= visible.maxX()
                && y >= visible.minY() && y <= visible.maxY();
    }

    private VisualizerCamera.VisibleRange clipped(VisualizerCamera.VisibleRange visible) {
        int minX = Math.max(visible.minX(), bounds.minX());
        int maxX = Math.min(visible.maxX(), bounds.maxX());
        int minY = Math.max(visible.minY(), bounds.minY());
        int maxY = Math.min(visible.maxY(), bounds.maxY());
        return minX > maxX || minY > maxY
                ? null
                : new VisualizerCamera.VisibleRange(minX, maxX, minY, maxY);
    }

    private void constrainCamera() {
        if (bounds == null) return;
        float width = bounds.maxX() - bounds.minX() + 1f;
        float length = bounds.maxY() - bounds.minY() + 1f;
        float margin = Math.max(
                MIN_CAMERA_MARGIN,
                Math.min(width, length) * CAMERA_MARGIN_FRACTION);
        camera.constrainToBounds(
                bounds.minX(),
                bounds.maxX() + 1f,
                bounds.minY(),
                bounds.maxY() + 1f,
                margin);
    }
}
