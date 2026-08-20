package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.ArrayList;
import java.util.List;

/** Dense immutable implementation of {@link StandingWaterSpillTopology}. */
final class DenseStandingWaterSpillTopology implements StandingWaterSpillTopology {
    private final WorldBounds bounds;
    private final int bodyCount;
    private final List<StandingWaterSpillConnection> connections;
    private final List<List<StandingWaterSpillConnection>> byBody;

    DenseStandingWaterSpillTopology(
            WorldBounds bounds,
            int bodyCount,
            List<StandingWaterSpillConnection> connections) {
        if (bounds == null || connections == null) {
            throw new IllegalArgumentException("standing-water spill topology inputs must not be null");
        }
        if (bodyCount < 0) throw new IllegalArgumentException("body count must be non-negative");

        List<List<StandingWaterSpillConnection>> mutableByBody = new ArrayList<>(bodyCount);
        for (int bodyId = 0; bodyId < bodyCount; bodyId++) {
            mutableByBody.add(new ArrayList<>());
        }
        for (StandingWaterSpillConnection connection : connections) {
            if (connection == null
                    || connection.firstBodyId() >= bodyCount
                    || connection.secondBodyId() >= bodyCount) {
                throw new IllegalArgumentException("spill connection references unknown water body");
            }
            mutableByBody.get(connection.firstBodyId()).add(connection);
            mutableByBody.get(connection.secondBodyId()).add(connection);
        }

        List<List<StandingWaterSpillConnection>> immutableByBody = new ArrayList<>(bodyCount);
        for (List<StandingWaterSpillConnection> bodyConnections : mutableByBody) {
            immutableByBody.add(List.copyOf(bodyConnections));
        }
        this.bounds = bounds;
        this.bodyCount = bodyCount;
        this.connections = List.copyOf(connections);
        this.byBody = List.copyOf(immutableByBody);
    }

    @Override
    public WorldBounds bounds() {
        return bounds;
    }

    @Override
    public int bodyCount() {
        return bodyCount;
    }

    @Override
    public List<StandingWaterSpillConnection> connections() {
        return connections;
    }

    @Override
    public List<StandingWaterSpillConnection> connectionsForBody(int bodyId) {
        if (bodyId < 0 || bodyId >= bodyCount) {
            throw new IllegalArgumentException("unknown standing-water body id: " + bodyId);
        }
        return byBody.get(bodyId);
    }
}
