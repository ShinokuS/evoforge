package io.github.evoforge.simulation.world.terrain;

import java.util.TreeMap;

import io.github.evoforge.simulation.definition.DefinitionCatalog;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;

public final class TerrainSystem {

    private final TerrainStorage storage;
    private final DefinitionCatalog<LandscapeDefinitionId> definitions;
    private final TerrainLookup lookup;
    private final TreeMap<Integer, Integer> zCounts = new TreeMap<>();
    private final TerrainSurfaceIndex surfaceIndex =
            new TerrainSurfaceIndex();
    private long revision;
    private final TerrainRevisionLookup revisions = () -> revision;
    private final TerrainExtentLookup extents = new TerrainExtentLookup() {
        @Override
        public boolean empty() {
            return zCounts.isEmpty();
        }

        @Override
        public int minZ() {
            requireTerrain();
            return zCounts.firstKey();
        }

        @Override
        public int maxZ() {
            requireTerrain();
            return zCounts.lastKey();
        }

        private void requireTerrain() {
            if (zCounts.isEmpty()) {
                throw new IllegalStateException("terrain is empty");
            }
        }
    };

    public TerrainSystem(
            TerrainStorage storage,
            DefinitionCatalog<LandscapeDefinitionId> definitions) {

        if (storage == null) {
            throw new IllegalArgumentException(
                    "storage must not be null");
        }

        if (definitions == null) {
            throw new IllegalArgumentException(
                    "definitions must not be null");
        }

        this.storage = storage;
        this.definitions = definitions;
        lookup = storage::find;
    }

    public TerrainLookup lookup() {
        return lookup;
    }

    public TerrainExtentLookup extents() {
        return extents;
    }

    public TerrainSurfaceLookup surfaces() {
        return surfaceIndex.lookup();
    }

    public TerrainRevisionLookup revisions() {
        return revisions;
    }

    public TerrainPlacementResult place(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        requireKnownDefinition(definitionId);

        if (storage.find(x, y, z) != null) {
            return TerrainPlacementResult.POSITION_OCCUPIED;
        }

        storage.put(x, y, z, definitionId);
        zCounts.merge(z, 1, Integer::sum);
        surfaceIndex.add(x, y, z);
        revision++;
        return TerrainPlacementResult.PLACED;
    }

    public TerrainReplacementResult replace(
            int x,
            int y,
            int z,
            LandscapeDefinitionId definitionId) {

        requireKnownDefinition(definitionId);

        if (storage.find(x, y, z) == null) {
            return TerrainReplacementResult.TERRAIN_ABSENT;
        }

        storage.put(x, y, z, definitionId);
        revision++;
        return TerrainReplacementResult.REPLACED;
    }

    public TerrainRemovalResult remove(
            int x,
            int y,
            int z) {

        if (storage.find(x, y, z) == null) {
            return TerrainRemovalResult.TERRAIN_ABSENT;
        }

        storage.remove(x, y, z);
        zCounts.computeIfPresent(
                z,
                (ignored, count) -> count == 1 ? null : count - 1);
        surfaceIndex.remove(x, y, z);
        revision++;
        return TerrainRemovalResult.REMOVED;
    }

    private void requireKnownDefinition(
            LandscapeDefinitionId definitionId) {

        if (definitionId == null) {
            throw new IllegalArgumentException(
                    "definitionId must not be null");
        }

        if (!definitions.contains(definitionId)) {
            throw new IllegalArgumentException(
                    "unknown landscape definition: " + definitionId);
        }
    }
}
