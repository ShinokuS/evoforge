package io.github.evoforge.simulation.world.calibration.soil;

import io.github.evoforge.simulation.definition.NormalizedValue;

/**
 * High-level authored soil character before any physical composition or hydraulic calibration.
 *
 * <p>{@code mineralFineness} is a continuous semantic axis from coarse mineral character at zero
 * toward fine mineral character at one. {@code organicMatter} is likewise a relative authored
 * tendency, not a physical mass fraction.</p>
 */
public record SoilSemanticProfile(
        NormalizedValue mineralFineness,
        NormalizedValue organicMatter) {

    public SoilSemanticProfile {
        if (mineralFineness == null || organicMatter == null) {
            throw new IllegalArgumentException("semantic soil profile fields must not be null");
        }
    }
}
