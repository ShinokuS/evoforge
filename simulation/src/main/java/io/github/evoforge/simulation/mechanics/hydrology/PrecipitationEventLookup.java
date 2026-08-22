package io.github.evoforge.simulation.mechanics.hydrology;

/** Read-only knowledge of whether this periodic precipitation source occupies a tick. */
@FunctionalInterface
public interface PrecipitationEventLookup {

    boolean occursAt(long tick);
}
