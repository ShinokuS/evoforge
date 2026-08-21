package io.github.evoforge.simulation.world.continuum.field;

/** A deterministic global scalar fact addressable without materializing the whole world. */
@FunctionalInterface
public interface ContinuumScalarField {
    double sample(long x, long y);
}
