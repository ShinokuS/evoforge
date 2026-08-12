package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/** Draws authoritative landscape state as a true horizontal Z slice. */
public final class LandscapeRenderer {

    private static final Color LOWER_DEPTH_ONE =
            new Color(0.39f, 0.42f, 0.38f, 1f);

    private final SimulationView view;
    private final ProceduralLandscapePack surfaceArt;
    private final ProceduralSliceArt sliceArt;
    private final LandscapeSliceResolver sliceResolver;

    public LandscapeRenderer(
            SimulationView view,
            ProceduralLandscapePack surfaceArt,
            ProceduralSliceArt sliceArt,
            LandscapeSliceResolver sliceResolver) {

        if (view == null) {
            throw new IllegalArgumentException("view must not be null");
        }
        if (surfaceArt == null) {
            throw new IllegalArgumentException("surfaceArt must not be null");
        }
        if (sliceArt == null) {
            throw new IllegalArgumentException("sliceArt must not be null");
        }
        if (sliceResolver == null) {
            throw new IllegalArgumentException("sliceResolver must not be null");
        }

        this.view = view;
        this.surfaceArt = surfaceArt;
        this.sliceArt = sliceArt;
        this.sliceResolver = sliceResolver;
    }

    /**
     * Draws one horizontal standing-Z cut.
     *
     * <p>Per XY cell the visual priority is:</p>
     * <ol>
     *   <li>solid terrain body anchored at selected Z;</li>
     *   <li>current surface supported by terrain at selected Z - 1;</li>
     *   <li>nearest lower surface visible through an otherwise open column.</li>
     * </ol>
     */
    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth) {

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawCell(
                        batch,
                        x,
                        y,
                        selectedStandingZ,
                        maxLowerDepth);
            }
        }

        drawCurrentRampDescentMarkers(
                batch,
                minX,
                maxX,
                minY,
                maxY,
                selectedStandingZ);

        batch.setColor(Color.WHITE);
    }

    private void drawCell(
            SpriteBatch batch,
            int x,
            int y,
            int selectedStandingZ,
            int maxLowerDepth) {

        LandscapeSliceResolver.Cell cell = sliceResolver.resolve(
                x,
                y,
                selectedStandingZ,
                maxLowerDepth);

        if (cell.kind() == LandscapeSliceResolver.Kind.EMPTY) {
            return;
        }

        int variant = LandscapeTopology.variant(
                x,
                y,
                cell.terrainZ(),
                ProceduralLandscapePack.SURFACE_VARIANTS);
        int topology = neighbourMask(x, y, cell.terrainZ());

        TextureRegion region;
        if (cell.kind() == LandscapeSliceResolver.Kind.SOLID_BODY) {
            region = cell.shape() instanceof RampShape ramp
                    ? sliceArt.rampCut(ramp, variant)
                    : sliceArt.solid(topology, variant);
            batch.setColor(Color.WHITE);
        } else {
            region = cell.shape() instanceof RampShape ramp
                    ? surfaceArt.ramp(ramp, variant)
                    : surfaceArt.surface(topology, variant);

            if (cell.kind() == LandscapeSliceResolver.Kind.LOWER_SURFACE) {
                setLowerTint(batch, cell.lowerDepth());
            } else {
                batch.setColor(Color.WHITE);
            }
        }

        batch.draw(region, x, y, 1f, 1f);
    }

    /**
     * A Ramp is authoritative only once, but its upper landing receives a
     * presentation-only descent mouth so the same connector reads in both
     * travel directions without inventing another Shape in simulation.
     */
    private void drawCurrentRampDescentMarkers(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ) {

        int terrainZ = selectedStandingZ - 1;
        batch.setColor(Color.WHITE);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                LandscapeSliceResolver.Cell rampCell = sliceResolver.resolve(
                        x,
                        y,
                        selectedStandingZ,
                        0);
                if (rampCell.kind()
                        != LandscapeSliceResolver.Kind.CURRENT_SURFACE
                        || !(rampCell.shape() instanceof RampShape ramp)) {
                    continue;
                }

                int landingX = x + riseX(ramp);
                int landingY = y + riseY(ramp);
                if (landingX < minX || landingX > maxX
                        || landingY < minY || landingY > maxY) {
                    continue;
                }

                LandscapeSliceResolver.Cell landing = sliceResolver.resolve(
                        landingX,
                        landingY,
                        selectedStandingZ,
                        0);
                if (landing.kind()
                        != LandscapeSliceResolver.Kind.CURRENT_SURFACE
                        || landing.terrainZ() != terrainZ) {
                    continue;
                }

                batch.draw(
                        sliceArt.descentMarker(ramp),
                        landingX,
                        landingY,
                        1f,
                        1f);
            }
        }
    }

    private static void setLowerTint(
            SpriteBatch batch,
            int depth) {

        float attenuation = 1f / (1f + (depth - 1) * 0.42f);
        batch.setColor(
                LOWER_DEPTH_ONE.r * attenuation,
                LOWER_DEPTH_ONE.g * attenuation,
                LOWER_DEPTH_ONE.b * attenuation,
                1f);
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

    private static int riseX(
            RampShape ramp) {

        if (ramp == RampShape.POSITIVE_X) {
            return 1;
        }
        if (ramp == RampShape.NEGATIVE_X) {
            return -1;
        }
        return 0;
    }

    private static int riseY(
            RampShape ramp) {

        if (ramp == RampShape.POSITIVE_Y) {
            return 1;
        }
        if (ramp == RampShape.NEGATIVE_Y) {
            return -1;
        }
        return 0;
    }
}
