package io.github.evoforge.simulation.world.geology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.world.genesis.GenerationRevision;
import io.github.evoforge.simulation.world.genesis.RngRevision;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class GeologyGenerationStageTest {

    @Test
    void v4GeneratesMultipleCoherentProvincesAndUnits() {
        WorldBounds bounds = new WorldBounds(-24, 24, -24, 24, -12, 12);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 42L);
        GeologyField geology = new GeologyGenerationStage().generate(genesis);
        Set<Long> provinces = new HashSet<>();
        Set<GeologyUnitKey> units = new HashSet<>();

        assertEquals(GenerationRevision.V4, genesis.generationRevision());
        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                provinces.add(geology.provinceIdAt(x, y));
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    units.add(geology.unitAt(x, y, z));
                }
            }
        }

        assertTrue(provinces.size() > 1);
        assertTrue(units.size() > 1);
    }

    @Test
    void identicalGenesisReplaysEveryGeologyFactExactly() {
        WorldBounds bounds = new WorldBounds(-10, 10, -9, 11, -8, 8);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 991L);
        GeologyGenerationStage stage = new GeologyGenerationStage();
        GeologyField first = stage.generate(genesis);
        GeologyField replay = stage.generate(genesis);

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(first.provinceIdAt(x, y), replay.provinceIdAt(x, y));
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    assertEquals(first.unitAt(x, y, z), replay.unitAt(x, y, z));
                }
            }
        }
    }

    @Test
    void overlappingWorldBoundsKeepSameGlobalGeologyFacts() {
        WorldBounds leftBounds = new WorldBounds(-20, 10, -10, 10, -12, 12);
        WorldBounds rightBounds = new WorldBounds(0, 30, -10, 10, -12, 12);
        WorldGenesis leftGenesis = WorldGenesis.current(new WorldSpec(leftBounds), 55L);
        WorldGenesis rightGenesis = WorldGenesis.current(new WorldSpec(rightBounds), 55L);
        GeologyGenerationStage stage = new GeologyGenerationStage();
        GeologyField left = stage.generate(leftGenesis);
        GeologyField right = stage.generate(rightGenesis);

        for (int y = -10; y <= 10; y++) {
            for (int x = 0; x <= 10; x++) {
                assertEquals(left.provinceIdAt(x, y), right.provinceIdAt(x, y));
                for (int z = -12; z <= 12; z++) {
                    assertEquals(left.unitAt(x, y, z), right.unitAt(x, y, z));
                }
            }
        }
    }

    @Test
    void preV4RevisionPreservesLegacyGraniteBedrockIdentity() {
        WorldBounds bounds = new WorldBounds(0, 3, 0, 3, -4, 4);
        WorldGenesis genesis = new WorldGenesis(
                new WorldSpec(bounds),
                7L,
                GenerationRevision.V3,
                RngRevision.V1);
        GeologyField geology = new GeologyGenerationStage().generate(genesis);

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    assertEquals(GeologyProfiles.GRANITE, geology.unitAt(x, y, z));
                }
            }
        }
    }

    @Test
    void authoredUnitOrderDoesNotChangeGeneratedFacts() {
        CompiledGeologyProfile firstProfile = GeologyProfiles.temperateCrust();
        CompiledGeologyProfile secondProfile = new GeologyProfileCompiler().compile(
                new GeologyProfileDefinition(
                        GeologyProfiles.TEMPERATE_CRUST,
                        java.util.List.of(
                                unit(GeologyProfiles.SHALE),
                                unit(GeologyProfiles.LIMESTONE),
                                unit(GeologyProfiles.GRANITE),
                                unit(GeologyProfiles.BASALT))));
        WorldBounds bounds = new WorldBounds(-8, 8, -8, 8, -8, 8);
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds), 123L);
        GeologyField first = new GeologyGenerationStage(firstProfile).generate(genesis);
        GeologyField second = new GeologyGenerationStage(secondProfile).generate(genesis);

        for (int y = bounds.minY(); y <= bounds.maxY(); y++) {
            for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
                assertEquals(first.provinceIdAt(x, y), second.provinceIdAt(x, y));
                for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                    assertEquals(first.unitAt(x, y, z), second.unitAt(x, y, z));
                }
            }
        }
    }

    private static GeologyProfileDefinition.UnitDefinition unit(GeologyUnitKey key) {
        return new GeologyProfileDefinition.UnitDefinition(
                key,
                GeologyMaterialKey.of(key.value()));
    }
}
