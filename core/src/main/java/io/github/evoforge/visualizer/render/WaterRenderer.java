package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.geometry.CellSpace;
import io.github.evoforge.simulation.world.space.measurement.CellVolume;
import io.github.evoforge.simulation.world.geometry.Shape;
import io.github.evoforge.visualizer.visual.ProceduralWaterArt;
import io.github.evoforge.visualizer.visual.WaterMotion;
import io.github.evoforge.visualizer.visual.WaterMotionResolver;
import io.github.evoforge.visualizer.visual.WaterOpticalDepthResolver;
import io.github.evoforge.visualizer.visual.WaterSliceResolver;

/**
 * Lightweight presentation of authoritative finite Water.
 *
 * <p>Only camera-visible XY cells are queried. Motion reads the sparse objective
 * latest-step Water transfer lookup; no presentation velocity field exists. Surface
 * view derives opacity from vertical Water depth, while legacy/interior slices retain
 * local-cell presentation. Hydraulically calm Water is deliberately static: later
 * wind/rain presentation may add ripples, but the renderer never invents flow at rest.
 */
public final class WaterRenderer {

    private static final long FLOW_FRAME_MILLIS = 85L;
    private static final float SURFACE_ALPHA_MIN = 0.18f;
    private static final float SURFACE_ALPHA_MAX = 0.96f;
    private static final float EXTINCTION_PER_CELL = 1.35f;
    private static final int ALPHA_LUT_SIZE = 256;
    private static final float[] SURFACE_ALPHA_LUT = buildSurfaceAlphaLut();

    private final SimulationView view;
    private final ProceduralWaterArt art;
    private final WaterSliceResolver sliceResolver;
    private final WaterOpticalDepthResolver opticalDepth;
    private final WaterMotionResolver motionResolver;

    public WaterRenderer(
            SimulationView view,
            ProceduralWaterArt art) {

        if (view == null || art == null) {
            throw new IllegalArgumentException(
                    "water renderer dependencies must not be null");
        }
        this.view = view;
        this.art = art;
        sliceResolver = new WaterSliceResolver(
                view.water(),
                view.geometry());
        opticalDepth = new WaterOpticalDepthResolver(view);
        motionResolver = new WaterMotionResolver(view.waterFlow(), view.water());
    }

    /** Draws Water visible from the open-world surface projection. */
    public void drawSurface(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        if (view.waterSurfaces().columnCount() == 0) return;

        int flowFrame = globalFrame(FLOW_FRAME_MILLIS);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                int depth = opticalDepth.visibleDepth(x, y);
                if (depth <= 0 || !view.waterSurfaces().hasColumn(x, y)) continue;

                int waterZ = view.waterSurfaces().topZ(x, y);
                WaterMotion motion = motionResolver.resolve(x, y, waterZ);
                int frame = presentationFrame(motion, x, y, 0, flowFrame);

                float alpha = surfaceOpacityForDepth(depth);
                float depthFraction = Math.min(
                        1f,
                        depth / (float) WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH);
                batch.setColor(
                        1f - depthFraction * 0.13f,
                        1f - depthFraction * 0.08f,
                        1f,
                        alpha);
                batch.draw(art.frame(motion, frame), x, y, 1f, 1f);
            }
        }
        batch.setColor(Color.WHITE);
    }

    /** Legacy/debug/interior cutaway Water rendering. */
    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth) {

        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
        }
        if (maxLowerDepth < 0) {
            throw new IllegalArgumentException(
                    "maxLowerDepth must not be negative");
        }
        if (view.waterSurfaces().columnCount() == 0) {
            return;
        }

        int flowFrame = globalFrame(FLOW_FRAME_MILLIS);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawCell(
                        batch,
                        x,
                        y,
                        selectedStandingZ,
                        maxLowerDepth,
                        flowFrame);
            }
        }
        batch.setColor(Color.WHITE);
    }

    private void drawCell(
            SpriteBatch batch,
            int x,
            int y,
            int selectedStandingZ,
            int maxLowerDepth,
            int flowFrame) {

        if (!view.waterSurfaces().hasColumn(x, y)) {
            return;
        }

        int waterZ = sliceResolver.resolve(
                x,
                y,
                selectedStandingZ,
                maxLowerDepth);
        if (waterZ == WaterSliceResolver.NO_WATER) {
            return;
        }

        int amount = view.water().amount(x, y, waterZ);
        if (amount <= 0) {
            return;
        }

        Shape shape = view.geometry().find(x, y, waterZ);
        int capacity = CellSpace.capacity(shape);
        if (capacity <= 0) {
            return;
        }

        int depth = selectedStandingZ - waterZ;
        float opacity = opacityFor(amount, capacity)
                * depthOpacity(depth);
        WaterMotion motion = motionResolver.resolve(x, y, waterZ);
        int frame = presentationFrame(motion, x, y, 0, flowFrame);

        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(
                art.frame(motion, frame),
                x,
                y,
                1f,
                1f);
    }

    static float opacityFor(
            int amount,
            int capacity) {

        if (amount <= 0 || capacity <= 0) {
            return 0f;
        }
        float fill = Math.min(
                1f,
                amount / (float) capacity);
        float eased = fill * (2f - fill);
        return 0.12f + 0.72f * eased;
    }

    /** Beer-Lambert-style depth opacity sampled through a tiny precomputed LUT. */
    static float surfaceOpacityForDepth(int normalizedDepth) {
        if (normalizedDepth <= 0) return 0f;
        int capped = Math.min(
                WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH,
                normalizedDepth);
        int index = Math.min(
                ALPHA_LUT_SIZE - 1,
                Math.max(
                        1,
                        Math.round(
                                capped / (float) WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH
                                        * (ALPHA_LUT_SIZE - 1))));
        return SURFACE_ALPHA_LUT[index];
    }

    static float depthOpacity(
            int depth) {

        if (depth <= 0) {
            return 1f;
        }
        return switch (Math.min(depth, 6)) {
            case 1 -> 0.92f;
            case 2 -> 0.80f;
            case 3 -> 0.68f;
            case 4 -> 0.57f;
            case 5 -> 0.48f;
            default -> 0.40f;
        };
    }

    static int presentationFrame(
            WaterMotion motion,
            int x,
            int y,
            int ignoredCalmFrame,
            int flowFrame) {
        if (motion == WaterMotion.CALM) {
            return 0;
        }
        int spatialPhase = switch (motion) {
            case EAST -> x;
            case WEST -> -x;
            case NORTH -> y;
            case SOUTH -> -y;
            case FALLING -> x * 3 + y * 5;
            case CALM -> 0;
        };
        return Math.floorMod(flowFrame + spatialPhase, ProceduralWaterArt.FRAME_COUNT);
    }

    private static int globalFrame(long frameMillis) {
        return (int) Math.floorMod(
                TimeUtils.millis() / frameMillis,
                ProceduralWaterArt.FRAME_COUNT);
    }

    private static float[] buildSurfaceAlphaLut() {
        float[] result = new float[ALPHA_LUT_SIZE];
        result[0] = 0f;
        float maxCells = WaterOpticalDepthResolver.MAX_OPTICAL_DEPTH
                / (float) CellVolume.FULL;
        double maxCurve = 1.0 - Math.exp(-EXTINCTION_PER_CELL * maxCells);
        for (int index = 1; index < result.length; index++) {
            float depthCells = maxCells * index / (result.length - 1f);
            double curve = (1.0 - Math.exp(-EXTINCTION_PER_CELL * depthCells))
                    / maxCurve;
            result[index] = SURFACE_ALPHA_MIN
                    + (SURFACE_ALPHA_MAX - SURFACE_ALPHA_MIN) * (float) curve;
        }
        return result;
    }
}
