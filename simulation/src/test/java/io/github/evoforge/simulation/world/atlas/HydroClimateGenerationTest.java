package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class HydroClimateGenerationTest {

    @Test
    void requestedHydrologicClimateBecomesAtlasNormalEverywhere() {
        WorldBounds bounds = new WorldBounds(-3, 4, -2, 5, -10, 10);
        HydroClimateSpec climate = HydroClimateSpec.of(
                CellVolumeRate.of(3_000L, 2L),
                CellVolumeRate.of(900L, 1L));
        WorldSpec spec = new WorldSpec(bounds, climate);

        HydroClimateField field = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(spec, 17L))
                .hydroClimate();

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(climate.precipitationSupply(), field.precipitationSupplyAt(x, y));
                assertEquals(climate.evaporativeDemand(), field.evaporativeDemandAt(x, y));
            }
        }
    }

    @Test
    void legacyWorldSpecConstructorIsExplicitlyUnforced() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        WorldSpec spec = new WorldSpec(bounds);

        assertEquals(HydroClimateSpec.UNFORCED, spec.hydroClimate());
        HydroClimateField field = new HydroClimateGenerationStage().generate(spec);
        assertEquals(CellVolumeRate.ZERO, field.precipitationSupplyAt(0, 0));
        assertEquals(CellVolumeRate.ZERO, field.evaporativeDemandAt(0, 0));
    }

    @Test
    void climateQueriesAndMissingSpecificationAreValidated() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 1, -2, 2);
        HydroClimateField field = new HydroClimateGenerationStage()
                .generate(new WorldSpec(bounds));

        assertThrows(IllegalArgumentException.class,
                () -> field.precipitationSupplyAt(2, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new WorldSpec(bounds, null));
        assertThrows(IllegalArgumentException.class,
                () -> HydroClimateSpec.of(null, CellVolumeRate.ZERO));
    }
}
