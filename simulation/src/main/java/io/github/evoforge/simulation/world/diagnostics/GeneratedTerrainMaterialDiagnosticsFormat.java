package io.github.evoforge.simulation.world.diagnostics;

/** Canonical compact text representation of generated terrain material composition. */
public final class GeneratedTerrainMaterialDiagnosticsFormat {
    private GeneratedTerrainMaterialDiagnosticsFormat() { }

    public static String line(GeneratedTerrainMaterialDiagnostics diagnostics) {
        if (diagnostics == null) {
            throw new IllegalArgumentException("diagnostics must not be null");
        }
        return "event=world.generated.terrain-materials"
                + " seed=" + diagnostics.masterSeed()
                + " generationRevision=" + diagnostics.generationRevision().value()
                + " rngRevision=" + diagnostics.rngRevision().value()
                + " profile=" + diagnostics.profileKey()
                + " bounds=" + diagnostics.bounds()
                + " terrainCells=" + diagnostics.terrainCells()
                + " terrainColumns=" + diagnostics.terrainColumns()
                + " surface=" + diagnostics.surfaceCounts()
                + " volume=" + diagnostics.volumeCounts();
    }
}
