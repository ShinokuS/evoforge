package io.github.evoforge.simulation.world.landscape.liquid;

import java.util.HashMap;
import java.util.Map;

/** Mutable-at-composition, frozen-at-runtime liquid transport definition registry. */
public final class LiquidTransportDefinitions implements LiquidTransportLookup {

    private final Map<LiquidTypeId, LiquidTransportProperties> definitions = new HashMap<>();
    private boolean frozen;

    public void put(
            LiquidTypeId type,
            LiquidTransportProperties properties) {
        if (frozen) {
            throw new IllegalStateException("liquid transport definitions are frozen");
        }
        if (type == null || properties == null) {
            throw new IllegalArgumentException(
                    "liquid transport definition must not contain null");
        }
        LiquidTransportProperties previous = definitions.putIfAbsent(type, properties);
        if (previous != null) {
            throw new IllegalStateException(
                    "liquid transport definition already exists: " + type);
        }
    }

    @Override
    public LiquidTransportProperties find(LiquidTypeId type) {
        if (type == null) return null;
        return definitions.get(type);
    }

    public boolean has(LiquidTypeId type) {
        return type != null && definitions.containsKey(type);
    }

    public void freeze() {
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }
}
