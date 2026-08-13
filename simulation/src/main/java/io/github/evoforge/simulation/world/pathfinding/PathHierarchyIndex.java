package io.github.evoforge.simulation.world.pathfinding;

import java.util.HashMap;
import java.util.Map;

import io.github.evoforge.simulation.world.mechanics.geometry.TransitionDirections;
import io.github.evoforge.simulation.world.mechanics.geometry.TransitionMask;
import io.github.evoforge.simulation.world.mechanics.traversal.TraversalChangeLookup;
import io.github.evoforge.simulation.world.navigation.NavigationLookup;

/** Derived, non-authoritative directed 3D cluster connectivity. */
public final class PathHierarchyIndex {

    private final NavigationLookup navigation;
    private final TraversalChangeLookup changes;
    private final PathHierarchyConfig config;
    private final Map<Cluster, Integer> outgoing = new HashMap<>();
    private final ClusterProbe lookupProbe = new ClusterProbe();
    private long observedRevision;
    private long cacheHits;
    private long cacheMisses;
    private long rebuiltClusters;
    private long navigationQueries;
    private long localInvalidations;
    private long globalInvalidations;

    public PathHierarchyIndex(
            NavigationLookup navigation,
            TraversalChangeLookup changes,
            PathHierarchyConfig config) {
        if (navigation == null || changes == null || config == null) {
            throw new IllegalArgumentException("hierarchy dependencies must not be null");
        }
        this.navigation = navigation;
        this.changes = changes;
        this.config = config;
        observedRevision = changes.revision();
    }

    public PathHierarchyConfig config() { return config; }
    public long traversalRevision() { return changes.revision(); }
    public int clusterX(int x) { return Math.floorDiv(x, config.sizeX()); }
    public int clusterY(int y) { return Math.floorDiv(y, config.sizeY()); }
    public int clusterZ(int z) { return Math.floorDiv(z, config.sizeZ()); }

    public int outgoingTransitions(int cx, int cy, int cz) {
        synchronize();
        lookupProbe.set(cx, cy, cz);
        Integer cached = outgoing.get(lookupProbe);
        if (cached != null) {
            cacheHits++;
            return cached;
        }
        cacheMisses++;
        int resolved = buildOutgoing(cx, cy, cz);
        outgoing.put(new Cluster(cx, cy, cz), resolved);
        rebuiltClusters++;
        return resolved;
    }

    public PathHierarchyIndexMetrics metrics() {
        synchronize();
        return new PathHierarchyIndexMetrics(
                outgoing.size(), cacheHits, cacheMisses, rebuiltClusters,
                navigationQueries, localInvalidations, globalInvalidations);
    }

    private void synchronize() {
        long current = changes.revision();
        if (current == observedRevision) return;
        if (observedRevision != Long.MAX_VALUE && current == observedRevision + 1) {
            invalidateAffectedSources(
                    changes.lastChangeX(), changes.lastChangeY(), changes.lastChangeZ());
        } else {
            outgoing.clear();
            globalInvalidations++;
        }
        observedRevision = current;
    }

    private void invalidateAffectedSources(int x, int y, int z) {
        long minX = Math.max((long) Integer.MIN_VALUE, (long) x - 1L);
        long maxX = Math.min((long) Integer.MAX_VALUE, (long) x + 1L);
        long minY = Math.max((long) Integer.MIN_VALUE, (long) y - 1L);
        long maxY = Math.min((long) Integer.MAX_VALUE, (long) y + 1L);
        long minZ = Math.max((long) Integer.MIN_VALUE, (long) z - 1L);
        long maxZ = Math.min((long) Integer.MAX_VALUE, (long) z + 2L);
        int minCx = floorCluster(minX, config.sizeX());
        int maxCx = floorCluster(maxX, config.sizeX());
        int minCy = floorCluster(minY, config.sizeY());
        int maxCy = floorCluster(maxY, config.sizeY());
        int minCz = floorCluster(minZ, config.sizeZ());
        int maxCz = floorCluster(maxZ, config.sizeZ());
        for (int cz = minCz; ; cz++) {
            for (int cy = minCy; ; cy++) {
                for (int cx = minCx; ; cx++) {
                    lookupProbe.set(cx, cy, cz);
                    if (outgoing.remove(lookupProbe) != null) localInvalidations++;
                    if (cx == maxCx) break;
                }
                if (cy == maxCy) break;
            }
            if (cz == maxCz) break;
        }
    }

    private int buildOutgoing(int cx, int cy, int cz) {
        long rawMinX = (long) cx * config.sizeX();
        long rawMinY = (long) cy * config.sizeY();
        long rawMinZ = (long) cz * config.sizeZ();
        long minX = Math.max((long) Integer.MIN_VALUE, rawMinX);
        long minY = Math.max((long) Integer.MIN_VALUE, rawMinY);
        long minZ = Math.max((long) Integer.MIN_VALUE, rawMinZ);
        long maxX = Math.min((long) Integer.MAX_VALUE, rawMinX + config.sizeX() - 1L);
        long maxY = Math.min((long) Integer.MAX_VALUE, rawMinY + config.sizeY() - 1L);
        long maxZ = Math.min((long) Integer.MAX_VALUE, rawMinZ + config.sizeZ() - 1L);
        if (minX > maxX || minY > maxY || minZ > maxZ) return TransitionMask.NONE;

        int result = TransitionMask.NONE;
        for (long z = minZ; z <= maxZ; z++) {
            for (long y = minY; y <= maxY; y++) {
                for (long x = minX; x <= maxX; x++) {
                    if (x != minX && x != maxX && y != minY && y != maxY
                            && z != minZ && z != maxZ) continue;
                    int transitions = navigation.transitions((int) x, (int) y, (int) z);
                    navigationQueries++;
                    for (int d = 0; d < TransitionDirections.COUNT; d++) {
                        if ((transitions & TransitionDirections.mask(d)) == 0) continue;
                        long tx = x + TransitionDirections.dx(d);
                        long ty = y + TransitionDirections.dy(d);
                        long tz = z + TransitionDirections.dz(d);
                        if (tx < Integer.MIN_VALUE || tx > Integer.MAX_VALUE
                                || ty < Integer.MIN_VALUE || ty > Integer.MAX_VALUE
                                || tz < Integer.MIN_VALUE || tz > Integer.MAX_VALUE) continue;
                        int ncx = clusterX((int) tx);
                        int ncy = clusterY((int) ty);
                        int ncz = clusterZ((int) tz);
                        int dx = ncx - cx;
                        int dy = ncy - cy;
                        int dz = ncz - cz;
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
                            throw new IllegalStateException(
                                    "immediate cell transition crossed non-adjacent hierarchy clusters");
                        }
                        result |= TransitionMask.of(dx, dy, dz);
                    }
                }
            }
        }
        return result;
    }

    private static int floorCluster(long cell, int size) {
        long cluster = Math.floorDiv(cell, (long) size);
        if (cluster < Integer.MIN_VALUE || cluster > Integer.MAX_VALUE) {
            throw new IllegalStateException("hierarchy cluster coordinate overflow");
        }
        return (int) cluster;
    }

    private static int hash(int x, int y, int z) {
        int result = Integer.hashCode(x);
        result = 31 * result + Integer.hashCode(y);
        result = 31 * result + Integer.hashCode(z);
        return result;
    }

    private record Cluster(int x, int y, int z) {
        @Override public int hashCode() { return hash(x, y, z); }
    }

    private static final class ClusterProbe {
        private int x;
        private int y;
        private int z;
        private void set(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        @Override public int hashCode() { return hash(x, y, z); }
        @Override public boolean equals(Object other) {
            if (other instanceof Cluster c) return x == c.x() && y == c.y() && z == c.z();
            if (other instanceof ClusterProbe p) return x == p.x && y == p.y && z == p.z;
            return false;
        }
    }
}
