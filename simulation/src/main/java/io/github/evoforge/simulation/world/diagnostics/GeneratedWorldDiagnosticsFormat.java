package io.github.evoforge.simulation.world.diagnostics;

/** Canonical compact text representation for explicit generated-world audit checkpoints. */
public final class GeneratedWorldDiagnosticsFormat {

    private GeneratedWorldDiagnosticsFormat() { }

    public static String line(GeneratedWorldDiagnostics diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }

        return "event=world.generated.audit"
                + " tick=" + diagnostics.tick()
                + " seed=" + diagnostics.masterSeed()
                + " generationRevision=" + diagnostics.generationRevision().value()
                + " rngRevision=" + diagnostics.rngRevision().value()
                + " bounds=" + diagnostics.bounds()
                + " terrainCells=" + diagnostics.terrainCells()
                + " terrainColumns=" + diagnostics.terrainColumns()
                + " surfaceMismatches=" + diagnostics.surfaceMismatches()
                + " surfaceZMin=" + diagnostics.minimumSurfaceZ()
                + " surfaceZMax=" + diagnostics.maximumSurfaceZ()
                + " geologyProvinces=" + diagnostics.geologyProvinces()
                + " geologyUnits=" + diagnostics.geologyUnits()
                + " terminalBasins=" + diagnostics.terminalBasins()
                + " maxContributingArea=" + diagnostics.maximumContributingArea()
                + " generatedInitialWater=" + diagnostics.generatedInitialWaterVolume()
                + " generatedInitialWaterColumns=" + diagnostics.generatedInitialWaterColumns()
                + " generatedShorelineColumns=" + diagnostics.generatedShorelineColumns()
                + " freeWater=" + diagnostics.freeWaterVolume()
                + " retainedWater=" + diagnostics.retainedWaterVolume()
                + " totalWater=" + diagnostics.totalWaterVolume()
                + " wetWaterCells=" + diagnostics.wetWaterCells()
                + " wetSoilCells=" + diagnostics.wetSoilCells()
                + " wetWaterColumns=" + diagnostics.wetWaterColumns()
                + " wetSoilColumns=" + diagnostics.wetSoilColumns()
                + " maxFreeWaterColumn=" + diagnostics.maximumFreeWaterColumnVolume()
                + " maxRetainedWaterColumn=" + diagnostics.maximumRetainedWaterColumnVolume()
                + " maxWetWaterCellsPerColumn=" + diagnostics.maximumWetWaterCellsPerColumn();
    }
}
