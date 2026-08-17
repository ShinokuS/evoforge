package io.github.evoforge.simulation.world.calibration.soil;

/** Replaceable semantic-to-physical preparation boundary for soil composition. */
@FunctionalInterface
public interface SoilCompositionCompiler {
    SoilCompositionProfile compile(SoilSemanticProfile semantic);
}
