package io.github.evoforge.simulation.world.bootstrap;

/**
 * Runtime composition policy for generated-world atmospheric forcing.
 *
 * <p>This is operational runtime configuration, not a climate classification and not a generated
 * world fact. {@link #CLIMATE_NORMALS} installs the current direct hydrologic projection of durable
 * climate normals; {@link #DISABLED} leaves the atmosphere outside the runtime so conservation and
 * isolated-system runs can use the same generated climate without changing it.</p>
 */
public enum AtmosphericForcingPolicy {
    CLIMATE_NORMALS,
    DISABLED
}
