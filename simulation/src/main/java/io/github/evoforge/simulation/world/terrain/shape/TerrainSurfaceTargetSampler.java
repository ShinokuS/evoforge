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

    /** Broad smooth V11 slopes become representable at actual one-level boundary crossings. */
    static TerrainSurfaceTargetSampler smoothVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::smoothVoxelTransitionPatch;
    }

    /** V12 keeps only locally coherent cardinal transition bands. */
    static TerrainSurfaceTargetSampler coherentVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::coherentVoxelTransitionPatch;
    }

    /**
     * V13 keeps the same geometry-only contract but samples coherent transition bands sparsely and
     * irregularly. It does not know which concrete Shape the later fitter will choose.
     */
    static TerrainSurfaceTargetSampler sparseCoherentVoxelTransitions() {
        return TerrainSurfaceTargetSamplers::sparseCoherentVoxelTransitionPatch;
    }
}
