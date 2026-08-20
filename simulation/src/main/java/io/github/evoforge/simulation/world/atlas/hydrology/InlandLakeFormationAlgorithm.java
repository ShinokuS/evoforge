package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Optional analytical conversion from derived depression facts into an additional inland-water
 * topology.
 *
 * <p>Production V15 does not use depression fill as a water author: Z=0 lake membership is already
 * part of authoritative terrain before mountains. The standard implementation therefore contributes
 * no extra water. {@link SpillLevelInlandLakeFormationAlgorithm} remains available as an explicitly
 * selected analytical/experimental implementation.</p>
 */
@FunctionalInterface
public interface InlandLakeFormationAlgorithm {

    InlandLakeTopology generate(
            ElevationField elevation,
            DrainageBasinTopology basins,
            InlandLakeFormationRecipe recipe);

    static InlandLakeFormationAlgorithm standard() {
        return NoAdditionalInlandLakeFormationAlgorithm.INSTANCE;
    }
}
