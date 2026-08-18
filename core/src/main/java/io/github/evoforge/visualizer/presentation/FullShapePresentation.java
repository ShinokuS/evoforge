package io.github.evoforge.visualizer.presentation;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import io.github.evoforge.simulation.world.mechanics.geometry.FullShape;
import io.github.evoforge.visualizer.visual.LandscapeTopology;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/** Procedural presentation binding for the default full terrain cell Shape. */
final class FullShapePresentation
        implements ShapePresentation<FullShape> {

    private final ProceduralLandscapePack surfaceArt;
    private final ProceduralSliceArt sliceArt;

    FullShapePresentation(
            ProceduralLandscapePack surfaceArt,
            ProceduralSliceArt sliceArt) {

        this.surfaceArt = surfaceArt;
        this.sliceArt = sliceArt;
    }

    @Override
    public TextureRegion terrainRegion(
            FullShape shape,
            int topologyMask,
            int variant,
            boolean solidBody) {

        return solidBody
                ? sliceArt.solid(topologyMask, variant)
                : surfaceArt.surface(LandscapeTopology.normalizeSurfaceArt(topologyMask), variant);
    }

    @Override
    public String debugLabel(
            FullShape shape) {

        return "FullShape";
    }
}
