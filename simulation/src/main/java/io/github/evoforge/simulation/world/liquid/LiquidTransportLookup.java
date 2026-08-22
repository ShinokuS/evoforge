package io.github.evoforge.simulation.world.liquid;

/** Read-only physical transport properties for liquid identities. */
@FunctionalInterface
public interface LiquidTransportLookup {

    LiquidTransportProperties find(LiquidTypeId type);

    default LiquidTransportProperties require(LiquidTypeId type) {
        if (type == null) {
            throw new IllegalArgumentException("liquid type must not be null");
        }
        LiquidTransportProperties properties = find(type);
        if (properties == null) {
            throw new IllegalStateException(
                    "liquid transport properties are not configured: " + type);
        }
        return properties;
    }
}
