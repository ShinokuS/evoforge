package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.soil.SoilProperties;
import io.github.evoforge.simulation.world.soil.SoilPropertiesLookup;

/**
 * Stable runtime reference whose authoritative source may be selected once during assembly.
 *
 * <p>Existing definition-backed Soil remains the fallback when no generated field is supplied.
 * Generated-world bootstrap may replace that source before start; after freeze the lookup is
 * immutable for the lifetime of the runtime.</p>
 */
final class PreStartSoilPropertiesLookup implements SoilPropertiesLookup {
    private final SoilPropertiesLookup fallback;
    private SoilPropertiesLookup authoritative;
    private boolean configured;
    private boolean frozen;

    PreStartSoilPropertiesLookup(SoilPropertiesLookup fallback) {
        if (fallback == null) {
            throw new IllegalArgumentException("fallback Soil lookup must not be null");
        }
        this.fallback = fallback;
    }

    void configure(SoilPropertiesLookup lookup) {
        if (lookup == null) {
            throw new IllegalArgumentException("resolved Soil lookup must not be null");
        }
        if (frozen) {
            throw new IllegalStateException("resolved Soil lookup is already frozen");
        }
        if (configured) {
            throw new IllegalStateException("resolved Soil lookup is already configured");
        }
        authoritative = lookup;
        configured = true;
    }

    void freeze() {
        frozen = true;
    }

    @Override
    public SoilProperties find(int x, int y, int z) {
        SoilPropertiesLookup selected = authoritative;
        return selected == null ? fallback.find(x, y, z) : selected.find(x, y, z);
    }
}
