package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.visualizer.VisualizerState;
import io.github.evoforge.visualizer.presentation.ShapePresentationRegistry;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.SurfaceProjectionResolver;
import io.github.evoforge.visualizer.visual.SurfaceReliefEdgeArt;
import io.github.evoforge.visualizer.visual.TerrainElevationColorRamp;
import io.github.evoforge.visualizer.visual.TerrainElevationTintShader;

/** Default open-world renderer: one highest terrain surface per visible XY column. */
public final class SurfaceLandscapeRenderer {

    private final SimulationView view;
    private final VisualizerState state;
    private final ShapePresentationRegistry shapePresentations;
    private final SurfaceProjectionResolver surfaces;
    private final SurfaceReliefEdgeArt reliefEdges = new SurfaceReliefEdgeArt();
    private final TerrainElevationTintShader elevationShader = new TerrainElevationTintShader();
    private final Color elevationColor = new Color();
    private final int minimumSurfaceZ;
    private final int maximumSurfaceZ;

    public SurfaceLandscapeRenderer(
            SimulationView view,
            VisualizerState state,
            ShapePresentationRegistry shapePresentations,
            SurfaceProjectionResolver surfaces) {
        if (view == null || state == null || shapePresentations == null || surfaces == null) {
            throw new IllegalArgumentException("surface renderer dependencies must not be null");
        }
        this.view = view;
        this.state = state;
        this.shapePresentations = shapePresentations;
        this.surfaces = surfaces;

        int[] range = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        view.terrainSurfaces().forEach((x, y, z) -> {
            range[0] = Math.min(range[0], z);
            range[1] = Math.max(range[1], z);
        });
        minimumSurfaceZ = range[0] == Integer.MAX_VALUE ? 0 : range[0];
        maximumSurfaceZ = range[1] == Integer.MIN_VALUE ? 0 : range[1];
    }

    public void draw(
            SpriteBatch batch,
            int minX,
            int maxX,
            int minY,
            int maxY) {
        if (batch == null) throw new IllegalArgumentException("batch must not be null");

        // Terrain elevation uses a dedicated shader so low ground can darken and high ground can
        // genuinely brighten without multiplying the whole atlas by a muddy absolute color.
        if (state.showElevationGradient()) elevationShader.apply(batch);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawTerrainCell(batch, x, y);
            }
        }
        if (state.showElevationGradient()) elevationShader.clear(batch);
        batch.setColor(Color.WHITE);

        // Relief edges share one tiny atlas and are drawn as one second texture pass.
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawReliefCell(batch, x, y);
            }
        }
        batch.setColor(Color.WHITE);
    }

    public void setFilter(Texture.TextureFilter filter) {
        reliefEdges.setFilter(filter);
    }

    public void dispose() {
        elevationShader.dispose();
        reliefEdges.dispose();
    }

    private void drawTerrainCell(SpriteBatch batch, int x, int y) {
        SurfaceProjectionResolver.SurfaceCell cell = surfaces.resolve(x, y);
        if (!cell.hasTerrain()) return;

        int z = cell.terrainZ();
        int variant = LandscapeTopology.variant(
                x,
                y,
                z,
                ProceduralLandscapePack.SURFACE_VARIANTS);
        int topology = neighbourMask(x, y, z);
        TextureRegion region = shapePresentations.terrainRegion(
                cell.shape(),
                topology,
                variant,
                false);
        if (state.showElevationGradient()) {
            batch.setColor(TerrainElevationColorRamp.shaderColor(
                    z,
                    minimumSurfaceZ,
                    maximumSurfaceZ,
                    TerrainElevationColorRamp.DEFAULT_SCENARIO_SENSITIVITY_PPM,
                    elevationColor));
        } else {
            batch.setColor(Color.WHITE);
        }
        batch.draw(region, x, y, 1f, 1f);
    }

    private void drawReliefCell(SpriteBatch batch, int x, int y) {
        if (!view.terrainSurfaces().hasColumn(x, y)) return;
        int z = view.terrainSurfaces().topZ(x, y);

        drawReliefEdge(batch, x, y, z, x, y + 1, SurfaceReliefEdgeArt.Side.NORTH);
        drawReliefEdge(batch, x, y, z, x + 1, y, SurfaceReliefEdgeArt.Side.EAST);
        drawReliefEdge(batch, x, y, z, x, y - 1, SurfaceReliefEdgeArt.Side.SOUTH);
        drawReliefEdge(batch, x, y, z, x - 1, y, SurfaceReliefEdgeArt.Side.WEST);
    }

    private void drawReliefEdge(
            SpriteBatch batch,
            int x,
            int y,
            int z,
            int neighbourX,
            int neighbourY,
            SurfaceReliefEdgeArt.Side side) {
        boolean neighbourPresent = view.terrainSurfaces().hasColumn(neighbourX, neighbourY);
        if (neighbourPresent && view.terrainSurfaces().topZ(neighbourX, neighbourY) == z) return;

        // Missing surface is visually a drop beyond the current tile.
        boolean raised = !neighbourPresent || z > view.terrainSurfaces().topZ(neighbourX, neighbourY);
        batch.draw(reliefEdges.region(side, raised), x, y, 1f, 1f);
    }

    private int neighbourMask(int x, int y, int z) {
        int mask = 0;
        if (sameSurface(x, y + 1, z)) mask |= LandscapeTopology.N;
        if (sameSurface(x + 1, y + 1, z)) mask |= LandscapeTopology.NE;
        if (sameSurface(x + 1, y, z)) mask |= LandscapeTopology.E;
        if (sameSurface(x + 1, y - 1, z)) mask |= LandscapeTopology.SE;
        if (sameSurface(x, y - 1, z)) mask |= LandscapeTopology.S;
        if (sameSurface(x - 1, y - 1, z)) mask |= LandscapeTopology.SW;
        if (sameSurface(x - 1, y, z)) mask |= LandscapeTopology.W;
        if (sameSurface(x - 1, y + 1, z)) mask |= LandscapeTopology.NW;
        return LandscapeTopology.normalize(mask);
    }

    private boolean sameSurface(int x, int y, int z) {
        return view.terrainSurfaces().hasColumn(x, y)
                && view.terrainSurfaces().topZ(x, y) == z;
    }
}
