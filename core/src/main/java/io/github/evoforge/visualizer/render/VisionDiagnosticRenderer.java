package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.agents.perception.vision.VisibleCell;
import io.github.evoforge.simulation.agents.perception.vision.VisibleObject;
import io.github.evoforge.simulation.agents.perception.vision.VisionSnapshot;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;

/** Generic developer overlay for the authoritative visual sense of the selected object. */
public final class VisionDiagnosticRenderer {
    private static final Color VISIBLE_CELL = new Color(0.20f, 0.78f, 0.66f, 0.18f);
    private static final Color VISIBLE_OBJECT = new Color(0.32f, 0.96f, 0.82f, 0.94f);
    private static final Color FACING = new Color(1.00f, 0.88f, 0.28f, 1f);
    private static final Color FACING_SHADOW = new Color(0.02f, 0.03f, 0.03f, 0.92f);

    private final SimulationView view;
    private final SimulationTime time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ShapeRenderer shapes = new ShapeRenderer();
    private ObjectId cachedObserver;
    private long cachedTick = Long.MIN_VALUE;
    private VisionSnapshot cachedSnapshot;

    public VisionDiagnosticRenderer(
            SimulationView view,
            SimulationTime time,
            VisualizerState state,
            VisualizerCamera camera) {
        if (view == null || time == null || state == null || camera == null) {
            throw new IllegalArgumentException("vision diagnostic dependencies must not be null");
        }
        this.view = view;
        this.time = time;
        this.state = state;
        this.camera = camera;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        ObjectId selected = state.selectedObject();
        if (selected == null) return;
        VisionSnapshot snapshot = snapshot(selected);
        if (snapshot == null) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(camera.projection());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(VISIBLE_CELL);
        for (VisibleCell cell : snapshot.cells()) {
            if (cell.z() != state.selectedZ() || !inside(range, cell.x(), cell.y())) continue;
            shapes.rect(cell.x() + 0.04f, cell.y() + 0.04f, 0.92f, 0.92f);
        }
        drawFacing(snapshot);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(VISIBLE_OBJECT);
        for (VisibleObject object : snapshot.objects()) {
            if (object.z() != state.selectedZ() || !inside(range, object.x(), object.y())) continue;
            shapes.rect(object.x() + 0.08f, object.y() + 0.08f, 0.84f, 0.84f);
        }
        shapes.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose() {
        shapes.dispose();
    }

    private VisionSnapshot snapshot(ObjectId observer) {
        long tick = time.tick();
        if (!observer.equals(cachedObserver) || tick != cachedTick) {
            cachedObserver = observer;
            cachedTick = tick;
            cachedSnapshot = view.vision().snapshot(observer);
        }
        return cachedSnapshot;
    }

    private void drawFacing(VisionSnapshot snapshot) {
        if (snapshot.originZ() != state.selectedZ()) return;
        FacingDirection facing = snapshot.facing();
        float magnitude = (float) StrictMath.sqrt((double) facing.x() * facing.x() + (double) facing.y() * facing.y());
        float ux = facing.x() / magnitude;
        float uy = facing.y() / magnitude;
        float startX = snapshot.originX() + 0.5f;
        float startY = snapshot.originY() + 0.5f;
        float endX = startX + ux * 0.42f;
        float endY = startY + uy * 0.42f;
        float shadowWidth = camera.worldUnitsPerPixel() * 5f;
        float lineWidth = camera.worldUnitsPerPixel() * 2.8f;

        shapes.setColor(FACING_SHADOW);
        shapes.rectLine(startX, startY, endX, endY, shadowWidth);
        drawArrowHead(endX, endY, ux, uy, 0.15f + camera.worldUnitsPerPixel() * 2f);
        shapes.setColor(FACING);
        shapes.rectLine(startX, startY, endX, endY, lineWidth);
        drawArrowHead(endX, endY, ux, uy, 0.15f);
    }

    private void drawArrowHead(float x, float y, float ux, float uy, float size) {
        float px = -uy;
        float py = ux;
        float backX = x - ux * size;
        float backY = y - uy * size;
        float half = size * 0.7f;
        shapes.triangle(x, y, backX + px * half, backY + py * half, backX - px * half, backY - py * half);
    }

    private static boolean inside(VisualizerCamera.VisibleRange range, int x, int y) {
        return x >= range.minX() && x <= range.maxX() && y >= range.minY() && y <= range.maxY();
    }
}
