package io.github.evoforge.simulation.world.warmup;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldBootstrap;
import io.github.evoforge.simulation.world.bootstrap.GeneratedWorldRuntime;
import io.github.evoforge.simulation.world.genesis.HydroClimateSpec;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.genesis.WorldSpec;
import io.github.evoforge.simulation.world.landscape.definition.LandscapeDefinitionId;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.spatial.WorldBounds;

final class GeneratedWorldWarmupFixture {

    private GeneratedWorldWarmupFixture() {
    }

    static GeneratedWorldRuntime create(
            long seed,
            HydroClimateSpec climate) {
        return create(seed, climate, bounds());
    }

    static GeneratedWorldRuntime create(
            long seed,
            HydroClimateSpec climate,
            WorldBounds bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException("bounds must not be null");
        }
        WorldGenesis genesis = WorldGenesis.current(
                new WorldSpec(bounds, climate),
                seed);

        SimulationAssembly assembly = SimulationAssembly.create();
        LandscapeDefinitionId ground = assembly.landscapeDefinition(
                "test:warmup-porous-ground");
        assembly.soilProperties(ground, 550_000, 100_000);

        return new GeneratedWorldBootstrap().create(
                genesis,
                assembly,
                TerrainMaterialResolver.uniform(ground));
    }

    static WorldBounds bounds() {
        return new WorldBounds(0, 3, 0, 3, -4, 4);
    }
}
