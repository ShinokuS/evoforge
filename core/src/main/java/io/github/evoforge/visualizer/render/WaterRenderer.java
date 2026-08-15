package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.visualizer.visual.ProceduralWaterArt;
import io.github.evoforge.visualizer.visual.WaterSliceResolver;

/**
 * Lightweight presentation of authoritative finite Water.
 *
 * <p>Only camera-visible XY cells are queried. Every visible water cell shares
 * one global animation phase, so adjacent tiles read as one surface without
 * per-cell animation state, particle allocation or renderer-owned fluid simulation.</p>
 */
public final class WaterRenderer {

    private static final long FRAME_MILLIS = 110L;

    private final SimulationView view;
    private final ProceduralWaterArt art;
    private final WaterSliceResolver sliceResolver;

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
    }

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

        int globalFrame = (int) Math.floorMod(
                TimeUtils.millis() / FRAME_MILLIS,
                ProceduralWaterArt.FRAME_COUNT);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawCell(
                        batch,
                        x,
                        y,
                        selectedStandingZ,
                        maxLowerDepth,
                        globalFrame);
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
            int globalFrame) {

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

        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(
                art.frame(globalFrame),
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
}
