package io.github.evoforge.simulation.world.materialization;

/** Immutable diagnostics for one completed generated-terrain materialization. */
public record TerrainMaterializationResult(
        long columns,
        long terrainCells) {

    public TerrainMaterializationResult {
        if (columns < 0L || terrainCells < 0L) {
            throw new IllegalArgumentException(
                    "materialization counts must be non-negative");
        }
        if (terrainCells < columns) {
            throw new IllegalArgumentException(
                    "every materialized column must contain terrain");
        }
    }
}
