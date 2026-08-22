package io.github.evoforge.simulation.world.interaction;

import java.util.List;

/** Immutable declarative set of physical target relations allowed by one interaction mechanic. */
public final class InteractionReachProfile {
    private final List<InteractionReachPattern> patterns;

    public InteractionReachProfile(List<InteractionReachPattern> patterns) {
        if (patterns == null || patterns.isEmpty()) {
            throw new IllegalArgumentException("interaction reach patterns must not be empty");
        }
        this.patterns = List.copyOf(patterns);
        for (InteractionReachPattern pattern : this.patterns) {
            if (pattern == null) throw new IllegalArgumentException("interaction reach pattern must not be null");
        }
    }

    public int count() { return patterns.size(); }
    public InteractionReachPattern patternAt(int index) { return patterns.get(index); }
}
