package io.github.evoforge.simulation.world.liquid;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

final class LiquidFlowActivity {

    private final Set<LiquidCell> active = new HashSet<>();

    void activate(int x, int y, int z) {
        active.add(new LiquidCell(x, y, z));
    }

    void activate(LiquidCell cell) {
        active.add(cell);
    }

    List<LiquidCell> snapshotSorted() {
        if (active.isEmpty()) return List.of();
        List<LiquidCell> snapshot = new ArrayList<>(active);
        snapshot.sort(null);
        return snapshot;
    }

    List<LiquidCell> drainSorted() {
        List<LiquidCell> snapshot = snapshotSorted();
        active.clear();
        return snapshot;
    }

    int size() {
        return active.size();
    }
}
