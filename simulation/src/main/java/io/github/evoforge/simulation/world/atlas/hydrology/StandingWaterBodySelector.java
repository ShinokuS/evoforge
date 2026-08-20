package io.github.evoforge.simulation.world.atlas.hydrology;

/**
 * Selects raw negative-Z standing-water components that are meaningful hydrologic bodies.
 *
 * <p>The selector is deliberately independent from later lake/sea/ocean semantics. It only decides
 * whether a geometric water component is substantial enough to participate in basin, spill and
 * river topology.</p>
 */
@FunctionalInterface
public interface StandingWaterBodySelector {
    StandingWaterTopology select(StandingWaterTopology rawTopology);
}
