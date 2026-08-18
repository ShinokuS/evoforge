package io.github.evoforge.simulation.world.terrain.shape;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Converts authoritative generated elevation into one material-agnostic local surface target.
 *
 * <p>The Shape fitter consumes only the returned geometry. A target sampler therefore may adapt
 * continuous generated morphology to voxel-scale transitions without naming or selecting a runtime
 * Shape.</p>
 */
@FunctionalInterface
public interface TerrainSurfaceTargetSampler {
    TerrainSurfacePatch sample(ElevationField elevation, int x, int y);

    /** Literal local interpolation used by the original surface-shape compiler. */
    static TerrainSurfaceTargetSampler precise() {
        return TerrainSurfaceTargetSamplers::precisePatch;
    }

    /**
     * Voxel-aware target used by V11: broad smooth slopes become representable where they actually
     * cross a one-level discrete surface boundary, while cliffs and flat regions remain literal.
     */
    static TerrainSurfaceTargetSampler smoothVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::smoothVoxelTransitionPatch;
    }
}
