package io.github.evoforge.simulation.agents.perception.vision;

/** Narrow world query for visual occlusion. */
public interface SightOcclusionLookup {
    boolean blocksSight(int x, int y, int z);
}
