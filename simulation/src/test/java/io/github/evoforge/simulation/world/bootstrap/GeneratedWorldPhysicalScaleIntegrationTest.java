package io.github.evoforge.simulation.world.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.ClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.scale.PhysicalSpaceScale;
import io.github.evoforge.simulation.world.spatial.WorldBounds;
import java.math.BigInteger;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class GeneratedWorldPhysicalScaleIntegrationTest {

    @Test
    void physicalSpaceLivesInGenesisWhilePhysicalTimeLivesInRuntimeComposition() {
        WorldBounds bounds = bounds();
        PhysicalSpaceScale space = PhysicalSpaceScale.cubicMillimeters(1_000L);
        SimulationTimeScale time = SimulationTimeScale.of(Duration.ofMillis(250L));
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds, ClimateSpec.STANDARD, space),
                42L);

        GeneratedWorldRuntime world = createScaled(genesis, time);

        assertEquals(space, world.atlas().genesis().spec().requirePhysicalSpaceScale());
        assertEquals(Optional.of(time), world.timeScale());
        assertEquals(Optional.of(BigInteger.ZERO), world.elapsedPhysicalNanoseconds());

        for (int tick = 0; tick < 4; tick++) {
            world.runtime().stepper().advance();
        }

        assertEquals(4L, world.runtime().time().tick());
        assertEquals(
                Optional.of(BigInteger.valueOf(1_000_000_000L)),
                world.elapsedPhysicalNanoseconds());
    }

    @Test
    void legacyUnscaledGeneratedRuntimeDoesNotInventPhysicalDimensions() {
        WorldGenesis genesis = WorldGenesis.current(new WorldSpec(bounds()), 42L);
        GeneratedWorldRuntime world = createUnscaled(genesis);

        assertTrue(world.atlas().genesis().spec().physicalSpaceScale().isEmpty());
        assertTrue(world.timeScale().isEmpty());
        assertTrue(world.elapsedPhysicalNanoseconds().isEmpty());
    }

    @Test
    void currentWholeMilliliterLiquidBridgeRejectsOnlyUnsupportedFractionalCellVolume() {
        PhysicalSpaceScale subMilliliter = PhysicalSpaceScale.cubicMillimeters(1L);
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds(), ClimateSpec.STANDARD, subMilliliter),
                42L);

        assertThrows(
                IllegalStateException.class,
                () -> createScaled(genesis, SimulationTimeScale.of(Duration.ofSeconds(1L))));
    }

    private static GeneratedWorldRuntime createScaled(
            WorldGenesis genesis,
            SimulationTimeScale timeScale) {
        SimulationAssembly assembly = assembly();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:scaled-ground");
        assembly.soilProperties(ground, 550_000, 100_000);
        return GeneratedWorldBootstrap.withTimeScale(
                        new WorldAtlasGenerator(),
                        AtmosphericForcingPolicy.DISABLED,
                        timeScale)
                .create(genesis, assembly, TerrainMaterialResolver.uniform(ground));
    }

    private static GeneratedWorldRuntime createUnscaled(WorldGenesis genesis) {
        SimulationAssembly assembly = assembly();
        LandscapeDefinitionId ground = assembly.landscapeDefinition("test:unscaled-ground");
        assembly.soilProperties(ground, 550_000, 100_000);
        return new GeneratedWorldBootstrap(
                        new WorldAtlasGenerator(),
                        AtmosphericForcingPolicy.DISABLED)
                .create(genesis, assembly, TerrainMaterialResolver.uniform(ground));
    }

    private static SimulationAssembly assembly() {
        return SimulationAssembly.create();
    }

    private static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }
}
