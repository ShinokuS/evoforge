package io.github.evoforge.simulation.world.genesis;

import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Terrain-only compatibility form of the historical WorldSpec.
 *
 * <p>The V12-V15 elevation algorithms consume only horizontal/vertical bounds. Historical climate
 * and physical-space metadata were merely copied through the V13/V14 base-genesis wrappers and
 * never participated in terrain calculations, so keeping only bounds preserves elevation semantics
 * while avoiding unrelated retired simulation dependencies.</p>
 */
public record WorldSpec(WorldBounds bounds) {
    public WorldSpec {
        if (bounds == null) throw new IllegalArgumentException("world bounds must not be null");
    }
}
