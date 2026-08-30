package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V14ExactDeepBathymetryPageSource;

/** Exact historical V14 terrain composition through coastal and deep-interior bathymetry. */
public final class V14ContinuumBathymetryPlan {
    private final V14ContinuumCoastalBathymetryPlan coastal;
    private final V14ExactDeepBathymetryPageSource elevationPages;

    private V14ContinuumBathymetryPlan(
            V14ContinuumCoastalBathymetryPlan coastal,
            V14ExactDeepBathymetryPageSource elevationPages) {
        this.coastal = coastal;
        this.elevationPages = elevationPages;
    }

    public static V14ContinuumBathymetryPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
        V14ContinuumCoastalBathymetryPlan coastal = V14ContinuumCoastalBathymetryPlan.prepare(
                domain,
                seed,
                terrainDefinition,
                mountainDefinition,
                minimumZCells,
                maximumZCells);
        V14ExactDeepBathymetryPageSource elevationPages = new V14ExactDeepBathymetryPageSource(
                domain,
                coastal.elevationPages(),
                coastal.calibration(),
                coastal.recipe());
        return new V14ContinuumBathymetryPlan(coastal, elevationPages);
    }

    public V14ContinuumCoastalBathymetryPlan coastal() {
        return coastal;
    }

    public V14BathymetryRecipe recipe() {
        return coastal.recipe();
    }

    public V14BathymetryCalibration calibration() {
        return coastal.calibration();
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
