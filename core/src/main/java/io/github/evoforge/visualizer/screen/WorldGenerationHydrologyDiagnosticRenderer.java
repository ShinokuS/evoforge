package io.github.evoforge.visualizer.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterBoundaryRoute;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterHydrologyTopology;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterRimCell;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterSpillConnection;
import io.github.evoforge.simulation.world.atlas.hydrology.StandingWaterTopology;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.visualizer.VisualizerCamera;

/** Presentation-only F4 overlay for inspecting Stage 2B standing-water topology facts. */
final class WorldGenerationHydrologyDiagnosticRenderer implements Disposable {
    private static final float FIT_PADDING = 1.08f;
    private static final float MIN_CAMERA_MARGIN = 2f;
    private static final float CAMERA_MARGIN_FRACTION = 0.03f;

    private static final Color BOUNDARY_WATER = new Color(0.10f, 0.46f, 0.92f, 0.20f);
    private static final Color ROUTED_WATER = new Color(0.12f, 0.86f, 0.62f, 0.24f);
    private static final Color CLOSED_WATER = new Color(0.94f, 0.24f, 0.50f, 0.28f);
    private static final Color RIM = new Color(0.98f, 0.82f, 0.18f, 0.72f);
    private static final Color SPILL = new Color(0.96f, 0.56f, 0.12f, 0.55f);
    private static final Color SELECTED_ROUTE = new Color(0.38f, 1f, 0.86f, 0.96f);

    private final VisualizerCamera camera = new VisualizerCamera();
    private final ShapeRenderer diagnostics = new ShapeRenderer(8_192);

    private WorldBounds bounds;
    private int viewportWidth = 1;
    private int viewportHeight = 1;

    void resize(int width, int height) {
        if (width <= 0 || height <= 0) return;
        viewportWidth = width;
        viewportHeight = height;
        camera.resize(width, height);
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

        drawBodies(topology, visible);
        drawInternalRims(topology, visible);
        drawSpillMeetings(topology, visible);
        drawSelectedRoutes(topology, visible);

        diagnostics.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    @Override
    public void dispose() {
        diagnostics.dispose();
    }

    private void drawBodies(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        StandingWaterTopology water = topology.standingWater();
        for (int x = visible.minX(); x <= visible.maxX(); x++) {
            for (int y = visible.minY(); y <= visible.maxY(); y++) {
                int bodyId = water.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY) continue;
                diagnostics.setColor(bodyColor(topology.boundaryRoutes().route(bodyId)));
                diagnostics.rect(x + 0.08f, y + 0.08f, 0.84f, 0.84f);
            }
        }
    }

    private void drawInternalRims(
            StandingWaterHydrologyTopology topology,
            VisualizerCamera.VisibleRange visible) {
        diagnostics.setColor(RIM);
        float marker = Math.max(0.12f, Math.min(0.30f, camera.worldUnitsPerPixel() * 3.2f));
        float half = marker * 0.5f;
        for (int bodyId = 0; bodyId < topology.bodyCount(); bodyId++) {
            if (topology.standingWater().body(bodyId).touchesWorldBoundary()) continue;
            for (StandingWaterRimCell rim : topology.rims().rimCells(bodyId)) {
                if (!rim.hasDryContinuation() || !visibleContains(visible, rim.x(), rim.y())) continue;
                diagnostics.rect(rim.x() + 0.5f - half, rim.y() + 0.5f - half, marker, marker);
            }
        }
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

    private static Color bodyColor(StandingWaterBoundaryRoute route) {
        if (route.boundaryConnected()) return BOUNDARY_WATER;
        return route.reachesBoundaryWater() ? ROUTED_WATER : CLOSED_WATER;
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
