package io.github.evoforge.simulation.world.landscape.soil;

@FunctionalInterface
public interface SoilMoistureLookup {

    int amount(int x, int y, int z);
}
