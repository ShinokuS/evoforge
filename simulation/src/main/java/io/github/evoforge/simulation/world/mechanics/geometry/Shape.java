package io.github.evoforge.simulation.world.mechanics.geometry;

public interface Shape {

    int transitionMask(
            int relativeX,
            int relativeY,
            int relativeZ);
}
