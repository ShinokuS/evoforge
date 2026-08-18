package io.github.evoforge.visualizer.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.runtime.SimulationView;
import io.github.evoforge.simulation.world.mechanics.geometry.CellFace;
import io.github.evoforge.simulation.world.mechanics.geometry.Shape;
import io.github.evoforge.simulation.world.mechanics.geometry.SurfaceBoundaryContinuity;
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

        if (state.showElevationGradient()) elevationShader.apply(batch);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                drawTerrainCell(batch, x, y);
            }
        }
        if (state.showElevationGradient()) elevationShader.clear(batch);
        batch.setColor(Color.WHITE);

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
        Shape shape = cell.shape();
        int variant = LandscapeTopology.variant(
                x,
                y,
                z,
                ProceduralLandscapePack.SURFACE_VARIANTS);
        int topology = neighbourMask(x, y, z, shape);
        TextureRegion region = shapePresentations.terrainRegion(
                shape,
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
        Shape shape = view.geometry().find(x, y, z);

        drawReliefEdge(batch, x, y, z, shape, x, y + 1, CellFace.POSITIVE_Y,
                SurfaceReliefEdgeArt.Side.NORTH);
        drawReliefEdge(batch, x, y, z, shape, x + 1, y, CellFace.POSITIVE_X,
                SurfaceReliefEdgeArt.Side.EAST);
        drawReliefEdge(batch, x, y, z, shape, x, y - 1, CellFace.NEGATIVE_Y,
                SurfaceReliefEdgeArt.Side.SOUTH);
        drawReliefEdge(batch, x, y, z, shape, x - 1, y, CellFace.NEGATIVE_X,
                SurfaceReliefEdgeArt.Side.WEST);
    }

    private void drawReliefEdge(
            SpriteBatch batch,
            int x,
            int y,
            int z,
            Shape shape,
            int neighbourX,
            int neighbourY,
            CellFace face,
            SurfaceReliefEdgeArt.Side side) {
        boolean neighbourPresent = view.terrainSurfaces().hasColumn(neighbourX, neighbourY);
        Shape neighbour = null;
        int neighbourZ = z;
        if (neighbourPresent) {
            neighbourZ = view.terrainSurfaces().topZ(neighbourX, neighbourY);
            neighbour = view.geometry().find(neighbourX, neighbourY, neighbourZ);
            if (SurfaceBoundaryContinuity.aligns(shape, z, face, neighbour, neighbourZ)) return;
        }

        // Boundary presentation is pair-owned. If either Shape owns the visual treatment of this
        // shared boundary, the generic earth overlay must stay out. This prevents an ordinary flat
        // neighbour from drawing the exact ramp-contact line that the ramp intentionally removes.
        if (!shapePresentations.genericReliefEdgeAllowed(shape, face)) return;
        if (neighbour != null
                && !shapePresentations.genericReliefEdgeAllowed(neighbour, face.opposite())) {
            return;
        }

        boolean raised = !neighbourPresent || z > neighbourZ;
        batch.draw(reliefEdges.region(side, raised), x, y, 1f, 1f);
    }

    private int neighbourMask(int x, int y, int z, Shape shape) {
        int mask = 0;
        if (joins(shape, z, x, y + 1, CellFace.POSITIVE_Y)) mask |= LandscapeTopology.N;
        if (sameDiscreteSurface(x + 1, y + 1, z)) mask |= LandscapeTopology.NE;
        if (joins(shape, z, x + 1, y, CellFace.POSITIVE_X)) mask |= LandscapeTopology.E;
        if (sameDiscreteSurface(x + 1, y - 1, z)) mask |= LandscapeTopology.SE;
        if (joins(shape, z, x, y - 1, CellFace.NEGATIVE_Y)) mask |= LandscapeTopology.S;
        if (sameDiscreteSurface(x - 1, y - 1, z)) mask |= LandscapeTopology.SW;
        if (joins(shape, z, x - 1, y, CellFace.NEGATIVE_X)) mask |= LandscapeTopology.W;
        if (sameDiscreteSurface(x - 1, y + 1, z)) mask |= LandscapeTopology.NW;
        return LandscapeTopology.normalize(mask);
    }

    private boolean joins(
            Shape shape,
            int z,
            int neighbourX,
            int neighbourY,
            CellFace face) {
        if (!view.terrainSurfaces().hasColumn(neighbourX, neighbourY)) return false;
        int neighbourZ = view.terrainSurfaces().topZ(neighbourX, neighbourY);
        Shape neighbour = view.geometry().find(neighbourX, neighbourY, neighbourZ);
        return SurfaceBoundaryContinuity.aligns(shape, z, face, neighbour, neighbourZ);
    }

    private boolean sameDiscreteSurface(int x, int y, int z) {
        return view.terrainSurfaces().hasColumn(x, y)
                && view.terrainSurfaces().topZ(x, y) == z;
    }
}
