package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;

/** Draws authoritative landscape state using the generated EvoForge tileset. */
public final class LandscapeRenderer {

    private static final Color CURRENT_TINT = Color.WHITE;
    private static final Color LOWER_TINT =
            new Color(0.39f, 0.42f, 0.38f, 1f);

    private final SimulationView view;
    private final ProceduralLandscapePack pack;

    public LandscapeRenderer(
            SimulationView view,
            ProceduralLandscapePack pack) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (pack == null) {
            throw new IllegalArgumentException("pack must not be null");
        }

        this.view = view;
        this.pack = pack;
    }

    /**
     * Draws a clean standing-Z slice. Lower terrain is contextual only and is
     * visible through cells where the selected standing plane has no support.
     */
    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            boolean showLowerContext) {

        if (showLowerContext) {
            drawPlane(
                    batch,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    selectedStandingZ - 1,
                    selectedStandingZ - 1,
                    true);
        }

        drawPlane(
                batch,
                minX,
                maxX,
                minY,
                maxY,
                selectedStandingZ,
                Integer.MIN_VALUE,
                false);

        batch.setColor(Color.WHITE);
    }

    private void drawPlane(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int standingZ,
            int occludingTerrainZ,
            boolean dimmed) {

        int terrainZ = standingZ - 1;
        batch.setColor(dimmed ? LOWER_TINT : CURRENT_TINT);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (!view.terrain().contains(x, y, terrainZ)) {
                    continue;
                }
                if (occludingTerrainZ != Integer.MIN_VALUE
                        && view.terrain().contains(x, y, occludingTerrainZ)) {
                    continue;
                }

                Shape shape = view.geometry().find(x, y, terrainZ);
                int variant = LandscapeTopology.variant(
                        x,
                        y,
                        terrainZ,
                        ProceduralLandscapePack.SURFACE_VARIANTS);

                TextureRegion region = shape instanceof RampShape ramp
                        ? pack.ramp(ramp, variant)
                        : pack.surface(
                                neighbourMask(x, y, terrainZ),
                                variant);

                batch.draw(region, x, y, 1f, 1f);
            }
        }
    }

    private int neighbourMask(
            int x,
            int y,
            int terrainZ) {

        int mask = 0;

        if (view.terrain().contains(x, y + 1, terrainZ)) {
            mask |= LandscapeTopology.N;
        }
        if (view.terrain().contains(x + 1, y + 1, terrainZ)) {
            mask |= LandscapeTopology.NE;
        }
        if (view.terrain().contains(x + 1, y, terrainZ)) {
            mask |= LandscapeTopology.E;
        }
        if (view.terrain().contains(x + 1, y - 1, terrainZ)) {
            mask |= LandscapeTopology.SE;
        }
        if (view.terrain().contains(x, y - 1, terrainZ)) {
            mask |= LandscapeTopology.S;
        }
        if (view.terrain().contains(x - 1, y - 1, terrainZ)) {
            mask |= LandscapeTopology.SW;
        }
        if (view.terrain().contains(x - 1, y, terrainZ)) {
            mask |= LandscapeTopology.W;
        }
        if (view.terrain().contains(x - 1, y + 1, terrainZ)) {
            mask |= LandscapeTopology.NW;
        }

        return LandscapeTopology.normalize(mask);
    }
}
