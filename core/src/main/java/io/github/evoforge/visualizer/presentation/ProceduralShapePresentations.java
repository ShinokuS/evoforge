package io.github.evoforge.visualizer.presentation;

import io.github.evoforge.simulation.world.geometry.FullShape;
import io.github.evoforge.simulation.world.geometry.RampShape;
import io.github.evoforge.visualizer.visual.ProceduralLandscapePack;
import io.github.evoforge.visualizer.visual.ProceduralSliceArt;

/** Composition root for the currently implemented procedural Shape bindings. */
public final class ProceduralShapePresentations {

    private ProceduralShapePresentations() {
    }

    public static ShapePresentationRegistry create(
            ProceduralLandscapePack surfaceArt,
            ProceduralSliceArt sliceArt) {

        ShapePresentationRegistry registry = new ShapePresentationRegistry();
        registry.register(
                FullShape.class,
                new FullShapePresentation(surfaceArt, sliceArt));
        registry.register(
                RampShape.class,
                new RampShapePresentation());
        return registry;
    }
}
