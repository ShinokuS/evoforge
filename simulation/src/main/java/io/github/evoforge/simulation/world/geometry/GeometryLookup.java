package io.github.evoforge.simulation.world.geometry;

public interface GeometryLookup {

    Shape find(
            int x,
            int y,
            int z);
}
