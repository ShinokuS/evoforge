package io.github.evoforge.simulation.world.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.HydroClimateField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class ClimateNormalsGenerationTest {

    @Test
    void oneAuthoredClimateBecomesAtlasNormalsAndRuntimeHydroProjection() {
        WorldBounds bounds = new WorldBounds(-3, 4, -2, 5, -10, 10);
        ClimateSpec climate = ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(18_000),
                300,
                CellVolumeRate.of(3_000L, 2L),
                CellVolumeRate.of(900L, 1L));
        WorldAtlas atlas = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(new WorldSpec(bounds, climate), 17L));

        ClimateNormalsField normals = atlas.climateNormals();
        HydroClimateField forcing = new ClimateHydroForcingView(normals);
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(climate.precipitationSupply(), normals.precipitationSupplyAt(x, y));
                assertEquals(climate.evaporativeDemand(), normals.evaporativeDemandAt(x, y));
                assertEquals(normals.precipitationSupplyAt(x, y), forcing.precipitationSupplyAt(x, y));
                assertEquals(normals.evaporativeDemandAt(x, y), forcing.evaporativeDemandAt(x, y));
            }
        }
    }

    @Test
    void v5TemperatureUsesPreciseElevationWhileV4KeepsUniformFallback() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -10, 10);
        ClimateSpec climate = ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(20_000),
                1_000,
                CellVolumeRate.ZERO,
                CellVolumeRate.ZERO);
        WorldSpec spec = new WorldSpec(bounds, climate);
        ElevationField elevation = new ElevationField() {
            @Override
            public WorldBounds bounds() {
                return bounds;
            }

            @Override
            public int elevationAt(int x, int y) {
                return x == 0 ? 1 : 3;
            }

            @Override
            public long elevationSubunitsAt(int x, int y) {
                return x == 0
                        ? 1_500_000L
                        : 3_250_000L;
            }
        };

        ClimateNormalsGenerationStage stage = new ClimateNormalsGenerationStage();
        ClimateNormalsField v4 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V4, RngRevision.V1), elevation);
        ClimateNormalsField v5 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V5, RngRevision.V1), elevation);

        assertEquals(20_000, v4.meanTemperatureAt(0, 0).milliCelsius());
        assertEquals(20_000, v4.meanTemperatureAt(1, 0).milliCelsius());
        assertEquals(18_500, v5.meanTemperatureAt(0, 0).milliCelsius());
        assertEquals(16_750, v5.meanTemperatureAt(1, 0).milliCelsius());
        assertNotEquals(v5.meanTemperatureAt(0, 0), v5.meanTemperatureAt(1, 0));
    }

    @Test
    void defaultWorldClimateIsExplicitlyUnforcedAndValidationIsStrict() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        WorldSpec spec = new WorldSpec(bounds);
        ClimateSpec climate = spec.climate();

        assertEquals(CellVolumeRate.ZERO, climate.precipitationSupply());
        assertEquals(CellVolumeRate.ZERO, climate.evaporativeDemand());
        assertThrows(IllegalArgumentException.class, () -> new WorldSpec(bounds, null));
        assertThrows(IllegalArgumentException.class,
                () -> ClimateTemperature.ofMilliCelsius(-273_151));
        assertThrows(IllegalArgumentException.class,
                () -> ClimateSpec.of(
                        ClimateTemperature.ofMilliCelsius(10_000),
                        -1,
                        CellVolumeRate.ZERO,
                        CellVolumeRate.ZERO));
    }
}
