package io.github.evoforge.simulation.world.agent.need;

/** Immutable definition of one per-object need level. Level 0 means fully satisfied. */
public record NeedSpec(NeedId id, long maxLevel, long initialLevel) {

    public NeedSpec {
        if (id == null) {
            throw new IllegalArgumentException("id must not be null");
        }
        if (maxLevel <= 0) {
            throw new IllegalArgumentException("maxLevel must be > 0");
        }
        if (initialLevel < 0 || initialLevel > maxLevel) {
            throw new IllegalArgumentException("initialLevel must be in [0, maxLevel]");
        }
    }
}
