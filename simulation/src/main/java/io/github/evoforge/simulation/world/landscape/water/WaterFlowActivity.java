package io.github.evoforge.simulation.world.landscape.water;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class WaterFlowActivity {

    private final Set<WaterCell> active = new HashSet<>();

    void activate(
            int x,
            int y,
            int z) {

        active.add(new WaterCell(x, y, z));
    }

    void activate(
            WaterCell cell) {

        active.add(cell);
    }

    List<WaterCell> drainSorted() {
        if (active.isEmpty()) {
            return List.of();
        }

        List<WaterCell> snapshot =
                new ArrayList<>(active);
        snapshot.sort(null);
        active.clear();
        return snapshot;
    }

    int size() {
        return active.size();
    }
}
