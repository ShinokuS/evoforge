package io.github.evoforge.simulation.world.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Emits compact structured summaries for explicit generated-world audit checkpoints. */
public final class GeneratedWorldDiagnosticsLog {

    private static final Logger LOG = LoggerFactory.getLogger(
            GeneratedWorldDiagnosticsLog.class);

    private GeneratedWorldDiagnosticsLog() {
    }

    public static void info(GeneratedWorldDiagnostics diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }

        LOG.info(
                "event=world.generated.audit tick={} seed={} generationRevision={} rngRevision={} "
                        + "bounds={} terrainCells={} terrainColumns={} surfaceMismatches={} "
                        + "surfaceZMin={} surfaceZMax={} terminalBasins={} maxContributingArea={} "
                        + "freeWater={} retainedWater={} totalWater={} wetWaterCells={} wetSoilCells={}",
                diagnostics.tick(),
                diagnostics.masterSeed(),
                diagnostics.generationRevision().value(),
                diagnostics.rngRevision().value(),
                diagnostics.bounds(),
                diagnostics.terrainCells(),
                diagnostics.terrainColumns(),
                diagnostics.surfaceMismatches(),
                diagnostics.minimumSurfaceZ(),
                diagnostics.maximumSurfaceZ(),
                diagnostics.terminalBasins(),
                diagnostics.maximumContributingArea(),
                diagnostics.freeWaterVolume(),
                diagnostics.retainedWaterVolume(),
                diagnostics.totalWaterVolume(),
                diagnostics.wetWaterCells(),
                diagnostics.wetSoilCells());
    }
}
