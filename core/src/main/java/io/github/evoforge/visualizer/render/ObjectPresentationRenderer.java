package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.time.SimulationTime;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentPhase;
import io.github.evoforge.simulation.world.agent.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.spatial.orientation.FacingDirection;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;

/** Presentation-only procedural object art driven exclusively by read-only simulation state. */
public final class ObjectPresentationRenderer {
    private static final Color GENERIC_EVEN = new Color(0.30f, 0.78f, 0.94f, 1f);
    private static final Color GENERIC_ODD = new Color(0.90f, 0.44f, 0.56f, 1f);
    private static final Color COW_BODY = new Color(0.92f, 0.88f, 0.76f, 1f);
    private static final Color COW_SPOT = new Color(0.18f, 0.16f, 0.14f, 1f);
    private static final Color COW_MUZZLE = new Color(0.78f, 0.57f, 0.50f, 1f);
    private static final Color OUTLINE = new Color(0.07f, 0.08f, 0.07f, 0.92f);
    private static final Color GRASS = new Color(0.30f, 0.66f, 0.29f, 1f);
    private static final Color CLOVER = new Color(0.25f, 0.58f, 0.31f, 1f);
    private static final Color DANDELION = new Color(0.37f, 0.64f, 0.25f, 1f);
    private static final Color FLOWER = new Color(0.98f, 0.78f, 0.16f, 1f);

    private final SimulationView view;
    private final SimulationTime time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ObjectPresentationBindings bindings;
    private final ShapeRenderer shapes = new ShapeRenderer();

    public ObjectPresentationRenderer(
            SimulationView view,
            SimulationTime time,
            VisualizerState state,
            VisualizerCamera camera,
            ObjectPresentationBindings bindings) {
        if (view == null || time == null || state == null || camera == null || bindings == null) {
            throw new IllegalArgumentException("object presentation renderer dependencies must not be null");
        }
        this.view = view;
        this.time = time;
        this.state = state;
        this.camera = camera;
        this.bindings = bindings;
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                int count = view.cells().objectCount(x, y, state.selectedZ());
                for (int index = 0; index < count; index++) {
                    ObjectId id = view.cells().objectAt(x, y, state.selectedZ(), index);
                    WorldObject object = view.objects().get(id);
                    if (object == null) continue;
                    ObjectPresentation presentation = bindings.get(object.definitionId());
                    boolean generic = presentation == null
                            || presentation.family() == ObjectVisualFamily.GENERIC;
                    float stack = Math.min(index, 3) * (generic ? 0.12f : 0.055f);
                    float cx = x + 0.5f + stack;
                    float cy = y + 0.5f - stack;
                    if (generic) {
                        drawGeneric(object, cx, cy);
                    } else if (presentation.family() == ObjectVisualFamily.CREATURE) {
                        drawCreature(id, cx, cy);
                    } else if (presentation.family() == ObjectVisualFamily.VEGETATION) {
                        drawVegetation(id, presentation.variant(), cx, cy);
                    }
                }
            }
        }
        shapes.end();
    }

    public void dispose() {
        shapes.dispose();
    }

    private void drawGeneric(WorldObject object, float cx, float cy) {
        shapes.setColor(object.definitionId().asInt() % 2 == 0 ? GENERIC_EVEN : GENERIC_ODD);
        shapes.circle(cx, cy, 0.22f, 16);
    }

    private void drawCreature(ObjectId id, float cx, float cy) {
        FacingDirection facing = view.orientations().has(id)
                ? view.orientations().facing(id)
                : FacingDirection.EAST;
        float fx = facing.x();
        float fy = facing.y();
        float px = -fy;
        float py = fx;

        AgentIntentTrace intent = view.agents().currentIntent(id);
        boolean using = intent != null && intent.phase() == AgentIntentPhase.USING_OPPORTUNITY;
        float chew = using ? ((time.tick() & 1L) == 0L ? 0.035f : -0.015f) : 0f;

        shapes.setColor(OUTLINE);
        shapes.circle(cx - fx * 0.09f, cy - fy * 0.09f, 0.285f, 22);
        shapes.circle(cx + fx * 0.11f, cy + fy * 0.11f, 0.255f, 22);
        shapes.setColor(COW_BODY);
        shapes.circle(cx - fx * 0.09f, cy - fy * 0.09f, 0.255f, 22);
        shapes.circle(cx + fx * 0.11f, cy + fy * 0.11f, 0.225f, 22);

        shapes.setColor(COW_SPOT);
        shapes.circle(cx - fx * 0.09f + px * 0.10f, cy - fy * 0.09f + py * 0.10f, 0.075f, 14);
        shapes.circle(cx + fx * 0.08f - px * 0.09f, cy + fy * 0.08f - py * 0.09f, 0.055f, 14);

        float headDistance = using ? 0.31f + chew : 0.29f;
        float hx = cx + fx * headDistance;
        float hy = cy + fy * headDistance;
        shapes.setColor(OUTLINE);
        shapes.circle(hx, hy, 0.145f, 18);
        shapes.setColor(COW_BODY);
        shapes.circle(hx, hy, 0.125f, 18);
        shapes.setColor(COW_MUZZLE);
        shapes.circle(hx + fx * 0.075f, hy + fy * 0.075f, 0.070f, 14);

        shapes.setColor(COW_SPOT);
        shapes.circle(hx + px * 0.045f - fx * 0.02f, hy + py * 0.045f - fy * 0.02f, 0.018f, 8);
        shapes.circle(hx - px * 0.045f - fx * 0.02f, hy - py * 0.045f - fy * 0.02f, 0.018f, 8);

        shapes.setColor(OUTLINE);
        for (int side = -1; side <= 1; side += 2) {
            shapes.circle(cx - fx * 0.15f + px * side * 0.21f, cy - fy * 0.15f + py * side * 0.21f, 0.045f, 10);
            shapes.circle(cx + fx * 0.12f + px * side * 0.20f, cy + fy * 0.12f + py * side * 0.20f, 0.045f, 10);
        }
    }

    private void drawVegetation(ObjectId id, int variant, float cx, float cy) {
        float fraction = 1f;
        if (view.consumableStocks().has(id)) {
            long capacity = view.consumableStocks().capacity(id);
            fraction = capacity <= 0L ? 0f : (float) view.consumableStocks().quantity(id) / (float) capacity;
        }
        fraction = Math.max(0f, Math.min(1f, fraction));
        int shoots = 2 + Math.round(fraction * 6f);
        float radius = 0.12f + fraction * 0.16f;
        Color foliage = switch (variant % 3) {
            case 1 -> CLOVER;
            case 2 -> DANDELION;
            default -> GRASS;
        };
        shapes.setColor(OUTLINE);
        shapes.circle(cx, cy, Math.max(0.07f, radius * 0.46f), 14);

        shapes.setColor(foliage);
        long seed = id.asLong() * 31L + variant * 17L;
        for (int index = 0; index < shoots; index++) {
            double angle = ((seed + index * 97L) & 1023L) / 1024.0 * Math.PI * 2.0;
            float distance = radius * (0.35f + (index % 3) * 0.20f);
            float sx = cx + (float) Math.cos(angle) * distance;
            float sy = cy + (float) Math.sin(angle) * distance;
            float size = 0.045f + fraction * 0.025f;
            shapes.circle(sx, sy, size, 10);
        }

        if (variant % 3 == 1 && fraction > 0.35f) {
            shapes.setColor(CLOVER);
            for (int i = 0; i < 3; i++) {
                double angle = i * Math.PI * 2.0 / 3.0;
                shapes.circle(
                        cx + (float) Math.cos(angle) * 0.07f,
                        cy + (float) Math.sin(angle) * 0.07f,
                        0.065f,
                        12);
            }
        } else if (variant % 3 == 2 && fraction > 0.25f) {
            shapes.setColor(FLOWER);
            shapes.circle(cx, cy, 0.065f + 0.025f * fraction, 14);
        }
    }
}
