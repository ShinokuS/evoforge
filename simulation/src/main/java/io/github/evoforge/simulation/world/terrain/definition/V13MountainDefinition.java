package io.github.evoforge.simulation.world.terrain.definition;

import io.github.evoforge.simulation.definition.NormalizedValue;

/** Authored V13 mountain controls preserved from the accepted historical generator. */
public record V13MountainDefinition(
        NormalizedValue abundance,
        NormalizedValue height,
        NormalizedValue scale,
        NormalizedValue chaininess,
        NormalizedValue peakSharpness,
        boolean plateausEnabled,
        NormalizedValue plateauProbability) {

    public V13MountainDefinition {
        if (abundance == null
                || height == null
                || scale == null
                || chaininess == null
                || peakSharpness == null
                || plateauProbability == null) {
            throw new IllegalArgumentException("mountain definition values must not be null");
        }
    }

    public static V13MountainDefinition balanced() {
        return new V13MountainDefinition(
                NormalizedValue.ofPartsPerMillion(350_000),
                NormalizedValue.ofPartsPerMillion(520_000),
                NormalizedValue.ofPartsPerMillion(500_000),
                NormalizedValue.ofPartsPerMillion(550_000),
                NormalizedValue.ofPartsPerMillion(600_000),
                true,
                NormalizedValue.ofPartsPerMillion(180_000));
    }
}
