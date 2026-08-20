package io.github.evoforge.simulation.world.atlas.hydrology;

import io.github.evoforge.simulation.world.atlas.ElevationField;

/**
 * Owns only broad closed-depression terrain morphology. It does not decide water membership,
 * water level, drainage routes or rivers.
 */
@FunctionalInterface
public interface LacustrineBasinMorphologyAlgorithm {

    LacustrineBasinTerrain generate(
            ElevationField baseElevation,
            LacustrineBasinMorphologyRecipe recipe);

    static LacustrineBasinMorphologyAlgorithm standard() {
        return new InteriorClosedDepressionBasinMorphologyAlgorithm();
    }
}
