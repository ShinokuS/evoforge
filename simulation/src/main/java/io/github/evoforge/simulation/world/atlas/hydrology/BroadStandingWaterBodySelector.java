package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Keeps only standing-water components with a genuine two-dimensional interior footprint.
 *
 * <p>A component qualifies when it contains at least one 2x2 block of water cells. This removes
 * isolated negative-Z pixels and one-cell-wide traces from hydrologic basin topology without
 * changing the accepted V14 terrain itself. The criterion is tied to grid resolution rather than
 * total world area: enlarging the world must not silently change whether the same local pond is a
 * hydrologic body.</p>
 */
public final class BroadStandingWaterBodySelector implements StandingWaterBodySelector {

    @Override
    public StandingWaterTopology select(StandingWaterTopology rawTopology) {
        if (rawTopology == null) throw new IllegalArgumentException("raw topology must not be null");

        WorldBounds bounds = rawTopology.bounds();
        int width = Math.toIntExact((long) bounds.maxX() - bounds.minX() + 1L);
        int height = Math.toIntExact((long) bounds.maxY() - bounds.minY() + 1L);
        boolean[] eligible = new boolean[rawTopology.bodyCount()];

        for (int y = bounds.minY(); y < bounds.maxY(); y++) {
            for (int x = bounds.minX(); x < bounds.maxX(); x++) {
                int bodyId = rawTopology.bodyIdAt(x, y);
                if (bodyId == StandingWaterTopology.NO_BODY || eligible[bodyId]) continue;
                if (rawTopology.bodyIdAt(x + 1, y) == bodyId
                        && rawTopology.bodyIdAt(x, y + 1) == bodyId
                        && rawTopology.bodyIdAt(x + 1, y + 1) == bodyId) {
                    eligible[bodyId] = true;
                }
            }
        }

        int[] remap = new int[rawTopology.bodyCount()];
        Arrays.fill(remap, StandingWaterTopology.NO_BODY);
        List<StandingWaterBody> selectedBodies = new ArrayList<>();
        for (int rawBodyId = 0; rawBodyId < rawTopology.bodyCount(); rawBodyId++) {
            if (!eligible[rawBodyId]) continue;
            StandingWaterBody raw = rawTopology.body(rawBodyId);
            int selectedId = selectedBodies.size();
            remap[rawBodyId] = selectedId;
            selectedBodies.add(new StandingWaterBody(
                    selectedId,
                    raw.cellCount(),
                    raw.shorelineEdgeCount(),
                    raw.touchesWorldBoundary(),
                    raw.minX(),
                    raw.maxX(),
                    raw.minY(),
                    raw.maxY()));
        }

        int[] selectedIds = new int[Math.multiplyExact(width, height)];
        Arrays.fill(selectedIds, StandingWaterTopology.NO_BODY);
        for (int localY = 0; localY < height; localY++) {
            int y = bounds.minY() + localY;
            for (int localX = 0; localX < width; localX++) {
                int x = bounds.minX() + localX;
                int rawBodyId = rawTopology.bodyIdAt(x, y);
                if (rawBodyId == StandingWaterTopology.NO_BODY) continue;
                int selectedId = remap[rawBodyId];
                if (selectedId != StandingWaterTopology.NO_BODY) {
                    selectedIds[localY * width + localX] = selectedId;
                }
            }
        }
        return new DenseStandingWaterTopology(bounds, selectedIds, selectedBodies);
    }
}
