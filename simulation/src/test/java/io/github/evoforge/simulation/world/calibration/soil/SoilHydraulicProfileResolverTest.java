package io.github.evoforge.simulation.world.calibration.soil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class SoilHydraulicProfileResolverTest {

    @Test
    void resolvesPhysicalHydraulicsOnlyAfterSemanticDefinitionCompilation() {
        TerrainMaterialKey coarseKey = TerrainMaterialKey.of("test:coarse");
        TerrainMaterialKey fineKey = TerrainMaterialKey.of("test:fine");
        SoilSemanticProfileBindings semantics = SoilSemanticProfileBindings.of(Map.of(
                coarseKey, profile(100_000, 400_000),
                fineKey, profile(800_000, 400_000)));

        SoilHydraulicProfileBindings hydraulics =
                SoilHydraulicProfileResolver.standard().resolve(semantics);

        assertEquals(2, hydraulics.asMap().size());
        assertTrue(compareRates(
                hydraulics.require(coarseKey).saturatedHydraulicConductivity(),
                hydraulics.require(fineKey).saturatedHydraulicConductivity()) > 0);
    }

    private static SoilSemanticProfile profile(int fineness, int organicMatter) {
        return new SoilSemanticProfile(
                NormalizedValue.ofPartsPerMillion(fineness),
                NormalizedValue.ofPartsPerMillion(organicMatter));
    }

    private static int compareRates(
            io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate left,
            io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate right) {
        return left.depthNanometersNumerator()
                .multiply(right.durationNanosecondsDenominator())
                .compareTo(right.depthNanometersNumerator()
                        .multiply(left.durationNanosecondsDenominator()));
    }
}
