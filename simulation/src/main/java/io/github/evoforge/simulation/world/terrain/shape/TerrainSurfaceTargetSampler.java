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

    /**
     * V12 target policy: the same geometry-only voxel transition, but only when neighbouring local
     * gradients support one coherent cardinal slope. This rejects isolated turns and contour curls
     * without naming or selecting a concrete runtime Shape.
     */
    static TerrainSurfaceTargetSampler coherentVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::coherentVoxelTransitionPatch;
    }

    /**
     * V13 target policy: retain only a sparse deterministic subset of coherent transition sites so
     * ramps remain distributed across broad slopes without consuming whole mountain faces.
     */
    static TerrainSurfaceTargetSampler sparseCoherentVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::sparseCoherentVoxelTransitionPatch;
    }
}
