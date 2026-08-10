package io.github.evoforge.simulation.world.mechanics.geometry;

public interface Shape {

    long transitionPorts(
            int relativeX,
            int relativeY,
            int relativeZ);

    default int transitionBlocks(
            int relativeX,
            int relativeY,
            int relativeZ) {

        return TransitionMask.NONE;
    }
}
