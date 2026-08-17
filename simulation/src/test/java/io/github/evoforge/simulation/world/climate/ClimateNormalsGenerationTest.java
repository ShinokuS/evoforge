package io.github.evoforge.simulation.world.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.evoforge.simulation.world.atlas.ElevationField;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.geometry.CellVolumeRate;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class ClimateNormalsGenerationTest {

    @Test
    void oneLegacyAuthoredClimateBecomesV7AtlasNormalsAndRuntimeProjection() {
        WorldBounds bounds = new WorldBounds(-3, 4, -2, 5, -10, 10);
        ClimateSpec climate = ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(18_000),
                300,
                CellVolumeRate.of(3_000L, 2L),
                CellVolumeRate.of(900L, 1L));
        WorldAtlas atlas = new WorldAtlasGenerator()
                .generate(WorldGenesis.current(new WorldSpec(bounds, climate), 17L));

        assertEquals(GenerationRevision.V7, atlas.genesis().generationRevision());
        ClimateNormalsField normals = atlas.climateNormals();
        ClimateHydroForcingView forcing = new ClimateHydroForcingView(normals);
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(climate.precipitationNormal(), normals.precipitationNormalAt(x, y));
                assertEquals(
                        climate.evaporativeDemandNormal(),
                        normals.evaporativeDemandNormalAt(x, y));
                assertEquals(
                        normals.precipitationNormalAt(x, y),
                        forcing.precipitationRateAt(x, y));
                assertEquals(
                        normals.evaporativeDemandNormalAt(x, y),
                        forcing.evaporativeDemandRateAt(x, y));
            }
        }
    }

    @Test
    void v5ThroughV7TemperatureUsePreciseElevationWhileV4KeepsUniformFallback() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -10, 10);
        ClimateSpec climate = ClimateSpec.of(
                ClimateTemperature.ofMilliCelsius(20_000),
                1_000,
                CellVolumeRate.ZERO,
                CellVolumeRate.ZERO);
        WorldSpec spec = new WorldSpec(bounds, climate);
        ElevationField elevation = elevation(bounds);

        ClimateNormalsGenerationStage stage = new ClimateNormalsGenerationStage();
        ClimateNormalsField v4 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V4, RngRevision.V1), elevation);
        ClimateNormalsField v5 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V5, RngRevision.V1), elevation);
        ClimateNormalsField v6 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V6, RngRevision.V1), elevation);
        ClimateNormalsField v7 = stage.generate(
                new WorldGenesis(spec, 1L, GenerationRevision.V7, RngRevision.V1), elevation);

        assertEquals(20_000, v4.meanTemperatureAt(0, 0).milliCelsius());
        assertEquals(20_000, v4.meanTemperatureAt(1, 0).milliCelsius());
        assertEquals(18_500, v5.meanTemperatureAt(0, 0).milliCelsius());
        assertEquals(16_750, v5.meanTemperatureAt(1, 0).milliCelsius());
        assertNotEquals(v5.meanTemperatureAt(0, 0), v5.meanTemperatureAt(1, 0));
        for (int x = 0; x <= 1; x++) {
            assertEquals(v5.meanTemperatureAt(x, 0), v6.meanTemperatureAt(x, 0));
            assertEquals(v6.meanTemperatureAt(x, 0), v7.meanTemperatureAt(x, 0));
            assertEquals(v5.precipitationNormalAt(x, 0), v7.precipitationNormalAt(x, 0));
            assertEquals(v5.evaporativeDemandNormalAt(x, 0), v7.evaporativeDemandNormalAt(x, 0));
        }
    }

    @Test
    void v8StoresPhysicalWaterDepthNormalsAndRejectsDimensionMixingAcrossRevisions() {
        WorldBounds bounds = new WorldBounds(0, 1, 0, 0, -10, 10);
        ClimateSpec physical = ClimateSpec.physical(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                WaterDepthRate.ofMillimeters(800L, Duration.ofDays(365L)),
                WaterDepthRate.ofMillimeters(600L, Duration.ofDays(365L)));
        WorldSpec physicalSpec = new WorldSpec(bounds, physical);
        ElevationField elevation = elevation(bounds);
        ClimateNormalsGenerationStage stage = new ClimateNormalsGenerationStage();

        ClimateNormalsField v8 = stage.generate(
                new WorldGenesis(physicalSpec, 3L, GenerationRevision.V8, RngRevision.V1), elevation);

        assertEquals(
                ClimateWaterNormal.Kind.PHYSICAL_WATER_DEPTH_PER_TIME,
                v8.waterNormalKind());
        assertEquals(physical.precipitationDepthNormal(), v8.precipitationDepthNormalAt(0, 0));
        assertEquals(
                physical.evaporativeDemandDepthNormal(),
                v8.evaporativeDemandDepthNormalAt(0, 0));
        assertThrows(IllegalStateException.class, () -> v8.precipitationNormalAt(0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> stage.generate(
                        new WorldGenesis(physicalSpec, 3L, GenerationRevision.V7, RngRevision.V1),
                        elevation));
        assertThrows(
                IllegalArgumentException.class,
                () -> stage.generate(
                        new WorldGenesis(
                                new WorldSpec(bounds, ClimateSpec.STANDARD),
                                3L,
                                GenerationRevision.V8,
                                RngRevision.V1),
                        elevation));
    }

    @Test
    void defaultWorldClimateIsAWorldFactRatherThanARuntimeMode() {
        WorldBounds bounds = new WorldBounds(0, 0, 0, 0, -1, 1);
        WorldSpec spec = new WorldSpec(bounds);
        ClimateSpec climate = spec.climate();

        assertEquals(ClimateSpec.STANDARD, climate);
        assertEquals(CellVolumeRate.of(1L, 1L), climate.precipitationNormal());
        assertEquals(CellVolumeRate.of(1L, 1L), climate.evaporativeDemandNormal());
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

    private static ElevationField elevation(WorldBounds bounds) {
        return new ElevationField() {
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
                return x == 0 ? 1_500_000L : 3_250_000L;
            }
        };
    }
}
