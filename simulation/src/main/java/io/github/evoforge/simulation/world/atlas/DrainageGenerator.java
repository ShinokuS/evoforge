package io.github.evoforge.simulation.world.atlas;

/** Authors closed-world drainage topology from an already generated elevation fact. */
@FunctionalInterface
public interface DrainageGenerator {
    DrainageField generate(ElevationField elevation);
}
