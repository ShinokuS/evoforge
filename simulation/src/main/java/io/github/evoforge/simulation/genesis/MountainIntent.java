package io.github.evoforge.simulation.genesis;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * Human-authored definition of mountain character.
 *
 * <p>All coordinates are semantic {@code 0..1} values. Exact mountain coverage, elevation,
 * horizontal scale, ridge geometry and plateau rules belong to generator calibration rather than
 * the authored definition.</p>
 */
public record MountainIntent(
        NormalizedValue abundance,
        NormalizedValue height,
        NormalizedValue scale,
        NormalizedValue chaininess,
        NormalizedValue peakSharpness,
        NormalizedValue plateauTendency) {

    public MountainIntent {
        if (abundance == null
                || height == null
                || scale == null
                || chaininess == null
                || peakSharpness == null
                || plateauTendency == null) {
            throw new IllegalArgumentException("mountain definition values must not be null");
        }
    }
}
