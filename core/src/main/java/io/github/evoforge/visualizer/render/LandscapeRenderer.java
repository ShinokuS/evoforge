package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.RampShape;
import io.github.evoforge.visualizer.visual.LandscapeSliceResolver;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/** Draws authoritative landscape using geometry-derived cutaway visibility. */
public final class LandscapeRenderer {

    private static final int EXPOSURE_DISTANCE = 12;

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

    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int selectedStandingZ,
            int maxLowerDepth) {

        LandscapeSliceResolver.Analysis analysis = sliceResolver.analyze(
                minX,
                maxX,
                minY,
                maxY,
                selectedStandingZ,
                maxLowerDepth,
                EXPOSURE_DISTANCE);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawCell(batch, analysis, x, y);
            }
        }

        batch.setColor(Color.WHITE);
    }

    private void drawCell(
            SpriteBatch batch,
            LandscapeSliceResolver.Analysis analysis,
            int x,
            int y) {

        LandscapeSliceResolver.Cell cell = analysis.resolve(x, y);
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
            // Ramp keeps exactly the same generated visual language on every
            // slice; only the environmental depth tint changes.
            region = cell.shape() instanceof RampShape ramp
                    ? surfaceArt.ramp(ramp, variant)
                    : sliceArt.solid(topology, variant);
            setSolidTint(batch, cell.bodyDepth());
        } else {
            region = cell.shape() instanceof RampShape ramp
                    ? surfaceArt.ramp(ramp, variant)
                    : surfaceArt.surface(topology, variant);
            setSurfaceTint(batch, cell);
        }

        batch.draw(region, x, y, 1f, 1f);
    }

    private static void setSolidTint(
            SpriteBatch batch,
            int bodyDepth) {

        float shade = switch (Math.min(bodyDepth, 5)) {
            case 1 -> 0.92f;
            case 2 -> 0.76f;
            case 3 -> 0.62f;
            case 4 -> 0.50f;
            default -> 0.40f;
        };
        batch.setColor(shade, shade, shade, 1f);
    }

    private static void setSurfaceTint(
            SpriteBatch batch,
            LandscapeSliceResolver.Cell cell) {

        float environment = environmentShade(cell);
        float drop = cell.kind() == LandscapeSliceResolver.Kind.LOWER_SURFACE
                ? dropShade(cell.dropDepth())
                : 1f;
        float shade = environment * drop;

        // A tiny cool bias helps covered/lower space separate from ordinary
        // grass without inventing a second cave material or lighting system.
        batch.setColor(
                shade * 0.96f,
                shade * 0.98f,
                shade,
                1f);
    }

    private static float environmentShade(
            LandscapeSliceResolver.Cell cell) {

        if (!cell.covered()) {
            return 1f;
        }

        float cover = Math.max(
                0.44f,
                0.80f - (Math.min(cell.coverDepth(), 6) - 1) * 0.075f);
        float tallCavernRelief = Math.min(
                0.08f,
                Math.max(0, cell.ceilingDistance() - 1) * 0.015f);
        float exposure = Math.max(
                0.48f,
                1f - Math.min(cell.exposureDistance(), EXPOSURE_DISTANCE + 1)
                        * 0.055f);

        return Math.max(
                0.28f,
                Math.min(0.86f, cover + tallCavernRelief) * exposure);
    }

    private static float dropShade(
            int depth) {

        if (depth <= 0) {
            return 1f;
        }
        return Math.max(0.50f, 1f - depth * 0.085f);
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
