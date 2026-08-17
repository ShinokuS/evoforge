package io.github.evoforge.simulation.world.calibration.soil;

/**
 * Compatibility name for the current representative continuous composition model.
 *
 * <p>The model is continuous; this class contains no authored texture categories or thresholds.
 * New composition wiring may depend directly on {@link ContinuousSoilCompositionCompiler}.</p>
 */
public final class RepresentativeSoilCompositionCompiler implements SoilCompositionCompiler {
    private final ContinuousSoilCompositionCompiler delegate =
            new ContinuousSoilCompositionCompiler(SoilCompositionCalibration.representative());

    @Override
    public SoilCompositionProfile compile(SoilSemanticProfile semantic) {
        return delegate.compile(semantic);
    }
}
