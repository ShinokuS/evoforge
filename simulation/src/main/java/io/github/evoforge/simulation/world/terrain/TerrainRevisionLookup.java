package io.github.evoforge.simulation.world.terrain;

/** Read-only monotonic version of authoritative terrain state. */
public interface TerrainRevisionLookup {

    long revision();
}
