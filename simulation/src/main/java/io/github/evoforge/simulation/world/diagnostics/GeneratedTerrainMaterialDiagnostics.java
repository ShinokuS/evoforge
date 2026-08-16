package io.github.evoforge.simulation.world.diagnostics;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.Map;
import java.util.TreeMap;

/** Immutable generated-material distribution snapshot before runtime mutation. */
public record GeneratedTerrainMaterialDiagnostics(
        long masterSeed,
        GenerationRevision generationRevision,
        RngRevision rngRevision,
        String profileKey,
        WorldBounds bounds,
        long terrainCells,
        int terrainColumns,
        Map<String, Long> surfaceCounts,
        Map<String, Long> volumeCounts) {

    public GeneratedTerrainMaterialDiagnostics {
        if (generationRevision == null
                || rngRevision == null
                || profileKey == null
                || profileKey.isBlank()
                || bounds == null
                || surfaceCounts == null
                || volumeCounts == null) {
            throw new IllegalArgumentException(
                    "generated terrain diagnostic fields must not be null/blank");
        }
        if (terrainCells < 0L || terrainColumns < 0) {
            throw new IllegalArgumentException(
                    "generated terrain diagnostic counts must not be negative");
        }
        surfaceCounts = immutableSorted(surfaceCounts);
        volumeCounts = immutableSorted(volumeCounts);
        if (sum(surfaceCounts) != terrainColumns) {
            throw new IllegalArgumentException(
                    "surface material counts must equal terrain column count");
        }
        if (sum(volumeCounts) != terrainCells) {
            throw new IllegalArgumentException(
                    "volume material counts must equal terrain cell count");
        }
    }

    public long surfaceCount(TerrainMaterialKey key) {
        return key == null ? 0L : surfaceCounts.getOrDefault(key.value(), 0L);
    }

    public long volumeCount(TerrainMaterialKey key) {
        return key == null ? 0L : volumeCounts.getOrDefault(key.value(), 0L);
    }

    private static Map<String, Long> immutableSorted(Map<String, Long> source) {
        TreeMap<String, Long> sorted = new TreeMap<>();
        for (Map.Entry<String, Long> entry : source.entrySet()) {
            if (entry.getKey() == null
                    || entry.getValue() == null
                    || entry.getValue() < 0L) {
                throw new IllegalArgumentException(
                        "generated terrain material counts must be non-negative and keyed");
            }
            sorted.put(entry.getKey(), entry.getValue());
        }
        return java.util.Collections.unmodifiableMap(sorted);
    }

    private static long sum(Map<String, Long> counts) {
        long sum = 0L;
        for (long count : counts.values()) {
            sum = Math.addExact(sum, count);
        }
        return sum;
    }
}
