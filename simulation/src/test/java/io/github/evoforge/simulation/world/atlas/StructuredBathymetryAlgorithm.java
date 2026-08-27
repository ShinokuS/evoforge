package io.github.evoforge.simulation.world.atlas;

import io.github.evoforge.simulation.world.genesis.WorldGenesis;

/**
 * Composition of accepted coastal bathymetry and independently replaceable deep-interior relief.
 */
public final class StructuredBathymetryAlgorithm implements BathymetryElevationAlgorithm {
    private final BathymetryElevationAlgorithm coastalAlgorithm;
    private final BathymetryElevationAlgorithm interiorAlgorithm;

    public StructuredBathymetryAlgorithm(
            BathymetryElevationAlgorithm coastalAlgorithm,
            BathymetryElevationAlgorithm interiorAlgorithm) {
        if (coastalAlgorithm == null || interiorAlgorithm == null) {
            throw new IllegalArgumentException("structured bathymetry algorithms must not be null");
        }
        this.coastalAlgorithm = coastalAlgorithm;
        this.interiorAlgorithm = interiorAlgorithm;
    }

    public static StructuredBathymetryAlgorithm standard() {
        return new StructuredBathymetryAlgorithm(
                new BathymetryMorphologyAlgorithm(),
                new DeepBathymetryStructureAlgorithm());
    }

    @Override
    public ElevationField generate(
            WorldGenesis genesis,
            ElevationField base,
            BathymetryCalibration calibration,
            BathymetryRecipe recipe) {
        ElevationField coastal = coastalAlgorithm.generate(genesis, base, calibration, recipe);
        if (coastal == null) throw new IllegalStateException("coastal bathymetry algorithm returned null");
        ElevationField structured = interiorAlgorithm.generate(genesis, coastal, calibration, recipe);
        if (structured == null) throw new IllegalStateException("interior bathymetry algorithm returned null");
        return structured;
    }
}
