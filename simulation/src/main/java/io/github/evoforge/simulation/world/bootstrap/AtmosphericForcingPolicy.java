package io.github.evoforge.simulation.world.bootstrap;

/**
 * Runtime composition policy for generated-world atmospheric forcing.
 *
 * <p>This is operational runtime configuration, not a climate classification and not a generated
 * world fact. {@link #CLIMATE_NORMALS} preserves the transitional direct projection of durable
 * climate normals. {@link #WEATHER_STATE} creates one mutable current-weather owner initialized
 * from climate temperature and routes atmospheric water through that state; until a weather driver
 * is installed it remains a calm initial condition. {@link #DISABLED} leaves atmospheric source
 * and sink processes outside the runtime.</p>
 */
public enum AtmosphericForcingPolicy {
    CLIMATE_NORMALS,
    WEATHER_STATE,
    DISABLED
}
