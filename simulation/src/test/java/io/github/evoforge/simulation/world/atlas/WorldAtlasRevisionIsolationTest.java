package io.github.evoforge.simulation.world.atlas;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import org.junit.jupiter.api.Test;

final class WorldAtlasRevisionIsolationTest {

    @Test
    void v6AddsHydrographyOwnershipWithoutChangingExistingV5WorldFacts() {
        WorldBounds bounds = new WorldBounds(-8, 8, -7, 9, -8, 8);
        WorldSpec spec = new WorldSpec(bounds);
        long seed = 991L;
        WorldAtlasGenerator generator = new WorldAtlasGenerator();

        WorldAtlas v5 = generator.generate(new WorldGenesis(
                spec,
                seed,
                GenerationRevision.V5,
                RngRevision.V1));
        WorldAtlas v6 = generator.generate(new WorldGenesis(
                spec,
                seed,
                GenerationRevision.V6,
                RngRevision.V1));

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(v5.elevation().elevationAt(x, y), v6.elevation().elevationAt(x, y));
                assertEquals(
                        v5.elevation().elevationSubunitsAt(x, y),
                        v6.elevation().elevationSubunitsAt(x, y));

                assertEquals(v5.geology().provinceIdAt(x, y), v6.geology().provinceIdAt(x, y));
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    assertEquals(v5.geology().unitAt(x, y, z), v6.geology().unitAt(x, y, z));
                }

                assertEquals(
                        v5.climateNormals().meanTemperatureAt(x, y),
                        v6.climateNormals().meanTemperatureAt(x, y));
                assertEquals(
                        v5.climateNormals().precipitationNormalAt(x, y),
                        v6.climateNormals().precipitationNormalAt(x, y));
                assertEquals(
                        v5.climateNormals().evaporativeDemandNormalAt(x, y),
                        v6.climateNormals().evaporativeDemandNormalAt(x, y));

                boolean hasDownstream = v5.drainage().hasDownstream(x, y);
                assertEquals(hasDownstream, v6.drainage().hasDownstream(x, y));
                if (hasDownstream) {
                    assertEquals(
                            v5.drainage().downstreamXAt(x, y),
                            v6.drainage().downstreamXAt(x, y));
                    assertEquals(
                            v5.drainage().downstreamYAt(x, y),
                            v6.drainage().downstreamYAt(x, y));
                }
                assertEquals(
                        v5.drainage().contributingAreaAt(x, y),
                        v6.drainage().contributingAreaAt(x, y));
                assertEquals(v5.drainage().terminalXAt(x, y), v6.drainage().terminalXAt(x, y));
                assertEquals(v5.drainage().terminalYAt(x, y), v6.drainage().terminalYAt(x, y));

                assertEquals(
                        v5.surfaceHydrology().initialWaterVolumeAt(x, y),
                        v6.surfaceHydrology().initialWaterVolumeAt(x, y));
                assertEquals(
                        v5.surfaceHydrology().isShoreline(x, y),
                        v6.surfaceHydrology().isShoreline(x, y));
                assertEquals(v5.hydrography().isChannelAt(x, y), v6.hydrography().isChannelAt(x, y));
                assertEquals(
                        v5.surfaceHydrology().isInitiallyWet(x, y),
                        v6.hydrography().isChannelAt(x, y),
                        "legacy v5 wet-channel footprint must become explicit v6 hydrography");
            }
        }
    }
}
