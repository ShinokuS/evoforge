package io.github.evoforge.simulation.world.navigation;

public interface NavigationLookup {

    int transitions(
            int x,
            int y,
            int z);
}
