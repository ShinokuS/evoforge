package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.TimeUtils;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.CellSpace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.visualizer.visual.ProceduralWaterArt;

/**
 * Lightweight presentation of authoritative finite Water.
 *
 * <p>Only camera-visible XY cells are queried. Every visible water cell shares
 * one global animation phase, so adjacent tiles read as one surface without
 * per-cell animation state, particle allocation or renderer-owned fluid simulation.</p>
 */
public final class WaterRenderer {

    private static final long FRAME_MILLIS = 180L;

    private final SimulationView view;
    private final ProceduralWaterArt art;

    public WaterRenderer(
            SimulationView view,
            ProceduralWaterArt art) {

        if (view == null || art == null) {
            throw new IllegalArgumentException(
                    "water renderer dependencies must not be null");
        }
        this.view = view;
        this.art = art;
    }

    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ) {

        if (batch == null) {
            throw new IllegalArgumentException("batch must not be null");
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
            int globalFrame) {

        if (!view.waterSurfaces().hasColumn(x, y)) {
            return;
        }

        int waterZ = view.waterSurfaces().topZ(x, y);
        if (!visibleAtSelectedPlane(
                x,
                y,
                waterZ,
                selectedStandingZ)) {
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

        float opacity = opacityFor(amount, capacity);
        if (waterZ < selectedStandingZ) {
            opacity *= 0.88f;
        }

        batch.setColor(1f, 1f, 1f, opacity);
        batch.draw(
                art.frame(globalFrame),
                x,
                y,
                1f,
                1f);
    }

    private boolean visibleAtSelectedPlane(
            int x,
            int y,
            int waterZ,
            int selectedStandingZ) {

        if (waterZ == selectedStandingZ) {
            return !view.terrain().contains(
                    x,
                    y,
                    selectedStandingZ);
        }

        // A Ramp may hold Water in its own terrain-anchor cell. Show this one
        // embedded level while its standing surface is selected; deeper water
        // remains hidden until the user changes Z rather than leaking through
        // arbitrary cutaway roofs.
        return waterZ == selectedStandingZ - 1
                && view.terrain().contains(x, y, waterZ)
                && CellSpace.capacity(
                        view.geometry().find(x, y, waterZ)) > 0;
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
        return 0.06f + 0.66f * eased;
    }
}
