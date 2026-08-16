package io.github.evoforge.simulation.world.diagnostics;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

/**
 * Immutable exact audit snapshot for one generated world runtime state.
 *
 * <p>The snapshot contains deterministic simulation facts only. Wall-clock timing,
 * renderer state and logging configuration are deliberately excluded so snapshots
 * from headless CI and desktop runs remain directly comparable.</p>
 */
public record GeneratedWorldDiagnostics(
        long tick,
        long masterSeed,
        GenerationRevision generationRevision,
        RngRevision rngRevision,
        WorldBounds bounds,
        long terrainCells,
        int terrainColumns,
        long surfaceMismatches,
        int minimumSurfaceZ,
        int maximumSurfaceZ,
        long terminalBasins,
        long maximumContributingArea,
        long freeWaterVolume,
        long retainedWaterVolume,
        long wetWaterCells,
        long wetSoilCells,
        int wetWaterColumns,
        int wetSoilColumns,
        long maximumFreeWaterColumnVolume,
        long maximumRetainedWaterColumnVolume,
        int maximumWetWaterCellsPerColumn) {

    public GeneratedWorldDiagnostics {
        if (tick < 0L) {
            throw new IllegalArgumentException("tick must not be negative");
        }
        if (generationRevision == null || rngRevision == null || bounds == null) {
            throw new IllegalArgumentException(
                    "generated world provenance must not be null");
        }
        if (terrainCells < 0L
                || terrainColumns < 0
                || surfaceMismatches < 0L
                || terminalBasins < 0L
                || maximumContributingArea < 0L
                || freeWaterVolume < 0L
                || retainedWaterVolume < 0L
                || wetWaterCells < 0L
                || wetSoilCells < 0L
                || wetWaterColumns < 0
                || wetSoilColumns < 0
                || maximumFreeWaterColumnVolume < 0L
                || maximumRetainedWaterColumnVolume < 0L
                || maximumWetWaterCellsPerColumn < 0) {
            throw new IllegalArgumentException(
                    "generated world diagnostic values must not be negative");
        }
        if (minimumSurfaceZ > maximumSurfaceZ) {
            throw new IllegalArgumentException(
                    "minimum surface must not exceed maximum surface");
        }
        if (wetWaterColumns > terrainColumns || wetSoilColumns > terrainColumns) {
            throw new IllegalArgumentException(
                    "wet column counts must not exceed terrain column count");
        }
        if (wetWaterColumns == 0
                && (freeWaterVolume != 0L
                        || wetWaterCells != 0L
                        || maximumFreeWaterColumnVolume != 0L
                        || maximumWetWaterCellsPerColumn != 0)) {
            throw new IllegalArgumentException(
                    "free Water distribution must be empty when no Water column is wet");
        }
        if (wetSoilColumns == 0
                && (retainedWaterVolume != 0L
                        || wetSoilCells != 0L
                        || maximumRetainedWaterColumnVolume != 0L)) {
            throw new IllegalArgumentException(
                    "retained Water distribution must be empty when no Soil column is wet");
        }
    }

    /** Whether runtime Terrain still exactly represents the generated Atlas surface. */
    public boolean surfaceMatchesAtlas() {
        return surfaceMismatches == 0L;
    }

    /** Total Water represented by free and Soil-retained volumes. */
    public long totalWaterVolume() {
        return Math.addExact(freeWaterVolume, retainedWaterVolume);
    }
}
