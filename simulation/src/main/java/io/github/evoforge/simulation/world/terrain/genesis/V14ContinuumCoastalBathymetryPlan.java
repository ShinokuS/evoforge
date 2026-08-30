package io.github.evoforge.simulation.world.terrain.genesis;

import io.github.evoforge.simulation.world.continuum.field.ContinuumScalarPageSource;
import io.github.evoforge.simulation.world.continuum.model.ContinuumWorldDomain;
import io.github.evoforge.simulation.world.terrain.definition.V13MountainDefinition;
import io.github.evoforge.simulation.world.terrain.definition.V15TerrainDefinition;
import io.github.evoforge.simulation.world.terrain.field.V14ExactCoastalBathymetryPageSource;

/** Exact historical V14 composition through the accepted coastal bathymetry pass. */
public final class V14ContinuumCoastalBathymetryPlan {
    private final V14ContinuumPreBathymetryPlan preBathymetry;
    private final V14BathymetryRecipe recipe;
    private final V14BathymetryCalibration calibration;
    private final V14ExactCoastalBathymetryPageSource elevationPages;

    private V14ContinuumCoastalBathymetryPlan(
            V14ContinuumPreBathymetryPlan preBathymetry,
            V14BathymetryRecipe recipe,
            V14BathymetryCalibration calibration,
            V14ExactCoastalBathymetryPageSource elevationPages) {
        this.preBathymetry = preBathymetry;
        this.recipe = recipe;
        this.calibration = calibration;
        this.elevationPages = elevationPages;
    }

    public static V14ContinuumCoastalBathymetryPlan prepare(
            ContinuumWorldDomain domain,
            long seed,
            V15TerrainDefinition terrainDefinition,
            V13MountainDefinition mountainDefinition,
            int minimumZCells,
            int maximumZCells) {
        if (domain == null || terrainDefinition == null || mountainDefinition == null) {
            throw new IllegalArgumentException("V14 coastal plan inputs must not be null");
        }
        V14BathymetryRecipe recipe = V14BathymetryRecipe.balanced();
        V14BathymetryCalibration calibration = V14BathymetryCalibration.compile(
                domain, minimumZCells, recipe);
        V14ContinuumPreBathymetryPlan preBathymetry = V14ContinuumPreBathymetryPlan.prepare(
                domain,
                seed,
                terrainDefinition,
                mountainDefinition,
                maximumZCells);
        V14ExactCoastalBathymetryPageSource elevationPages = new V14ExactCoastalBathymetryPageSource(
                domain,
                preBathymetry.elevationPages(),
                calibration,
                recipe);
        return new V14ContinuumCoastalBathymetryPlan(
                preBathymetry,
                recipe,
                calibration,
                elevationPages);
    }

    public V14ContinuumPreBathymetryPlan preBathymetry() {
        return preBathymetry;
    }

    public V14BathymetryRecipe recipe() {
        return recipe;
    }

    public V14BathymetryCalibration calibration() {
        return calibration;
    }

    public ContinuumScalarPageSource elevationPages() {
        return elevationPages;
    }
}
