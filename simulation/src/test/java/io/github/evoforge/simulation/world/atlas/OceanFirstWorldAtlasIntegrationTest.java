package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.evoforge.simulation.definition.NormalizedValue;
import io.github.evoforge.simulation.world.climate.ClimateTemperature;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenerationIntent;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.mechanics.measurement.WaterDepthRate;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.time.Duration;
import org.junit.jupiter.api.Test;

final class OceanFirstWorldAtlasIntegrationTest {

    @Test
    void v9IntentGeneratesCompleteAtlasWithCalibratedLandCoverage() {
        WorldBounds bounds = new WorldBounds(-8, 7, -8, 7, -8, 8);
        ClimateSpec climate = ClimateSpec.physical(
                ClimateTemperature.ofMilliCelsius(12_000),
                250,
                WaterDepthRate.ofMillimeters(1, Duration.ofDays(1)),
                WaterDepthRate.ofMillimeters(1, Duration.ofDays(1)));
        WorldGenerationIntent intent = new WorldGenerationIntent(
                NormalizedValue.ofPartsPerMillion(375_000),
                NormalizedValue.ofPartsPerMillion(700_000),
                NormalizedValue.ofPartsPerMillion(250_000));
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds, climate),
                991L,
                GenerationRevision.V9,
                RngRevision.V1,
                intent);

        WorldAtlas atlas = new WorldAtlasGenerator().generate(genesis);

        assertEquals(genesis, atlas.genesis());
        assertNotNull(atlas.geology());
        assertNotNull(atlas.climateNormals());
        assertNotNull(atlas.drainage());
        assertNotNull(atlas.hydrography());
        assertNotNull(atlas.surfaceHydrology());

        int land = 0;
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                if (atlas.elevation().elevationSubunitsAt(x, y) > 0L) land++;
            }
        }
        assertEquals(96, land);
    }
}
