package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.kernel.time.SimulationTime;
import io.github.evoforge.simulation.agents.decision.AgentIntentPhase;
import io.github.evoforge.simulation.agents.decision.AgentIntentTrace;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.space.orientation.FacingDirection;
import io.github.evoforge.visualizer.VisualizerCamera;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.VisualizerViewMode;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentation;
import io.github.evoforge.visualizer.presentation.object.ObjectPresentationBindings;
import io.github.evoforge.visualizer.presentation.object.ObjectVisualFamily;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;

/** Presentation-only procedural object art driven exclusively by read-only simulation state. */
public final class ObjectPresentationRenderer {
    private static final Color GENERIC_EVEN = new Color(0.30f, 0.78f, 0.94f, 1f);
    private static final Color GENERIC_ODD = new Color(0.90f, 0.44f, 0.56f, 1f);
    private static final Color COW_BODY = new Color(0.92f, 0.88f, 0.76f, 1f);
    private static final Color COW_SPOT = new Color(0.18f, 0.16f, 0.14f, 1f);
    private static final Color COW_MUZZLE = new Color(0.78f, 0.57f, 0.50f, 1f);
    private static final Color OUTLINE = new Color(0.07f, 0.08f, 0.07f, 0.92f);
    private static final Color PLANT_SHADOW = new Color(0.08f, 0.16f, 0.08f, 0.88f);
    private static final Color GRASS = new Color(0.28f, 0.73f, 0.29f, 1f);
    private static final Color GRASS_LIGHT = new Color(0.48f, 0.84f, 0.36f, 1f);
    private static final Color CLOVER = new Color(0.22f, 0.62f, 0.28f, 1f);
    private static final Color CLOVER_LIGHT = new Color(0.40f, 0.76f, 0.39f, 1f);
    private static final Color DANDELION = new Color(0.31f, 0.67f, 0.24f, 1f);
    private static final Color FLOWER = new Color(1.00f, 0.81f, 0.15f, 1f);
    private static final Color FLOWER_CENTER = new Color(0.72f, 0.49f, 0.08f, 1f);

    private final SimulationView view;
    private final SimulationTime time;
    private final VisualizerState state;
    private final VisualizerCamera camera;
    private final ObjectPresentationBindings bindings;
    private final SurfaceProjectionResolver surfaces;
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
        surfaces = new SurfaceProjectionResolver(view);
    }

    public void draw(VisualizerCamera.VisibleRange range) {
        shapes.setProjectionMatrix(camera.projection());
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int x = range.minX(); x <= range.maxX(); x++) {
            for (int y = range.minY(); y <= range.maxY(); y++) {
                int z = visibleZ(x, y);
                if (z == SurfaceProjectionResolver.NO_Z) continue;
                int count = view.cells().objectCount(x, y, z);
                for (int index = 0; index < count; index++) {
                    ObjectId id = view.cells().objectAt(x, y, z, index);
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

    private int visibleZ(int x, int y) {
        if (state.viewMode() == VisualizerViewMode.SURFACE) {
            return surfaces.standingZ(x, y);
        }
        if (state.viewMode() == VisualizerViewMode.INTERIOR) {
            if (state.interior() == null
                    || x < state.interior().minX() || x > state.interior().maxX()
                    || y < state.interior().minY() || y > state.interior().maxY()
                    || state.selectedZ() < state.interior().minZ()
                    || state.selectedZ() > state.interior().maxZ()) {
                return SurfaceProjectionResolver.NO_Z;
            }
        }
        return state.selectedZ();
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
        float fraction = stockFraction(id);
        shapes.setColor(PLANT_SHADOW);
        shapes.circle(cx, cy, 0.10f + fraction * 0.12f, 18);

        switch (Math.floorMod(variant, 3)) {
            case 1 -> drawClover(id, cx, cy, fraction);
            case 2 -> drawDandelion(id, cx, cy, fraction);
            default -> drawGrass(id, cx, cy, fraction);
        }
    }

    private void drawGrass(ObjectId id, float cx, float cy, float fraction) {
        int bladeCount = Math.max(2, 3 + Math.round(fraction * 7f));
        float maxLength = 0.10f + fraction * 0.24f;
        long seed = id.asLong() * 73L + 19L;
        for (int index = 0; index < bladeCount; index++) {
            double angle = ((seed + index * 137L) & 2047L) / 2048.0 * Math.PI * 2.0;
            float length = maxLength * (0.70f + (index % 4) * 0.08f);
            float ux = (float) Math.cos(angle);
            float uy = (float) Math.sin(angle);
            float px = -uy;
            float py = ux;
            float baseOffset = 0.025f + (index % 3) * 0.012f;
            float bx = cx + ux * baseOffset;
            float by = cy + uy * baseOffset;
            float halfWidth = 0.025f + fraction * 0.010f;
            shapes.setColor(index % 3 == 0 ? GRASS_LIGHT : GRASS);
            shapes.triangle(
                    bx + px * halfWidth,
                    by + py * halfWidth,
                    bx - px * halfWidth,
                    by - py * halfWidth,
                    cx + ux * length,
                    cy + uy * length);
        }
        shapes.setColor(GRASS_LIGHT);
        shapes.circle(cx, cy, 0.045f + fraction * 0.025f, 12);
    }

    private void drawClover(ObjectId id, float cx, float cy, float fraction) {
        int stems = Math.max(1, 1 + Math.round(fraction * 3f));
        long seed = id.asLong() * 41L + 7L;
        for (int stem = 0; stem < stems; stem++) {
            double angle = ((seed + stem * 521L) & 2047L) / 2048.0 * Math.PI * 2.0;
            float distance = 0.06f + fraction * (0.07f + stem * 0.015f);
            float ux = (float) Math.cos(angle);
            float uy = (float) Math.sin(angle);
            float tx = cx + ux * distance;
            float ty = cy + uy * distance;
            shapes.setColor(CLOVER);
            shapes.rectLine(cx, cy, tx, ty, 0.022f);
            float leafRadius = 0.045f + fraction * 0.025f;
            shapes.setColor(CLOVER_LIGHT);
            for (int leaf = 0; leaf < 3; leaf++) {
                double leafAngle = angle + leaf * Math.PI * 2.0 / 3.0;
                shapes.circle(
                        tx + (float) Math.cos(leafAngle) * leafRadius * 0.62f,
                        ty + (float) Math.sin(leafAngle) * leafRadius * 0.62f,
                        leafRadius,
                        14);
            }
            shapes.setColor(CLOVER);
            shapes.circle(tx, ty, leafRadius * 0.34f, 10);
        }
    }

    private void drawDandelion(ObjectId id, float cx, float cy, float fraction) {
        int leaves = Math.max(3, 4 + Math.round(fraction * 5f));
        long seed = id.asLong() * 53L + 11L;
        for (int index = 0; index < leaves; index++) {
            double angle = ((seed + index * 293L) & 2047L) / 2048.0 * Math.PI * 2.0;
            float ux = (float) Math.cos(angle);
            float uy = (float) Math.sin(angle);
            float px = -uy;
            float py = ux;
            float length = 0.10f + fraction * (0.12f + (index % 3) * 0.018f);
            float halfWidth = 0.035f;
            shapes.setColor(DANDELION);
            shapes.triangle(
                    cx + px * halfWidth,
                    cy + py * halfWidth,
                    cx - px * halfWidth,
                    cy - py * halfWidth,
                    cx + ux * length,
                    cy + uy * length);
        }
        if (fraction > 0.35f) {
            float flowerRadius = 0.055f + fraction * 0.030f;
            shapes.setColor(FLOWER);
            for (int petal = 0; petal < 8; petal++) {
                double angle = petal * Math.PI / 4.0;
                shapes.circle(
                        cx + (float) Math.cos(angle) * flowerRadius * 0.72f,
                        cy + (float) Math.sin(angle) * flowerRadius * 0.72f,
                        flowerRadius * 0.52f,
                        10);
            }
            shapes.setColor(FLOWER_CENTER);
            shapes.circle(cx, cy, flowerRadius * 0.52f, 12);
        }
    }

    private float stockFraction(ObjectId id) {
        if (!view.consumableStocks().has(id)) return 1f;
        long capacity = view.consumableStocks().capacity(id);
        if (capacity <= 0L) return 0f;
        return Math.max(0f, Math.min(1f,
                (float) view.consumableStocks().quantity(id) / (float) capacity));
    }
}
