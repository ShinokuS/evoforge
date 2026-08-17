package io.github.evoforge.simulation.world.bootstrap;

import io.github.evoforge.simulation.runtime.SimulationAssembly;
import io.github.evoforge.simulation.time.SimulationTimeScale;
import io.github.evoforge.simulation.world.atlas.WorldAtlas;
import io.github.evoforge.simulation.world.atlas.WorldAtlasGenerator;
import io.github.evoforge.simulation.world.genesis.WorldGenesis;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialBindings;
import io.github.evoforge.simulation.world.materialization.TerrainMaterialResolver;
import io.github.evoforge.simulation.world.preparation.GeneratedWorldPreparation;
import io.github.evoforge.simulation.world.preparation.PreparedGeneratedWorld;
import io.github.evoforge.simulation.world.terrain.generation.CompiledTerrainProfile;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerationStage;
import io.github.evoforge.simulation.world.terrain.generation.TerrainMaterialGenerator;

/**
 * Convenience facade preserving the historical one-call generated-world startup API.
 *
 * <p>The actual ownership boundary is explicit underneath: {@link GeneratedWorldPreparation} is a
 * pure preparation phase and {@link GeneratedWorldRuntimeBootstrap} consumes only already prepared
 * data. New architecture-sensitive code should prefer those two phases directly.</p>
 */
public final class GeneratedWorldBootstrap {
    private final GeneratedWorldPreparation preparation;
    private final GeneratedWorldRuntimeBootstrap runtimeBootstrap;

    public GeneratedWorldBootstrap() {
        this(
                new GeneratedWorldPreparation(),
                new GeneratedWorldRuntimeBootstrap());
    }

    public GeneratedWorldBootstrap(WorldAtlasGenerator atlasGenerator) {
        this(
                new GeneratedWorldPreparation(atlasGenerator),
                new GeneratedWorldRuntimeBootstrap());
    }

    @SuppressWarnings("removal")
    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        this(
                new GeneratedWorldPreparation(atlasGenerator),
                new GeneratedWorldRuntimeBootstrap(requirePolicy(atmosphericForcingPolicy).runtimePlan()));
    }

    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator) {
        this(
                new GeneratedWorldPreparation(atlasGenerator, terrainMaterialGenerator),
                new GeneratedWorldRuntimeBootstrap());
    }

    @SuppressWarnings("removal")
    public GeneratedWorldBootstrap(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy) {
        this(
                new GeneratedWorldPreparation(atlasGenerator, terrainMaterialGenerator),
                new GeneratedWorldRuntimeBootstrap(requirePolicy(atmosphericForcingPolicy).runtimePlan()));
    }

    /** Open replacement for the legacy AtmosphericForcingPolicy selector. */
    public GeneratedWorldBootstrap(
            GeneratedWorldPreparation preparation,
            GeneratedWorldRuntimeBootstrap runtimeBootstrap) {
        if (preparation == null || runtimeBootstrap == null) {
            throw new IllegalArgumentException("generated world composition phases must not be null");
        }
        this.preparation = preparation;
        this.runtimeBootstrap = runtimeBootstrap;
    }

    public static GeneratedWorldBootstrap withTimeScale(SimulationTimeScale timeScale) {
        return new GeneratedWorldBootstrap(
                new GeneratedWorldPreparation(),
                GeneratedWorldRuntimeBootstrap.withTimeScale(timeScale));
    }

    @SuppressWarnings("removal")
    public static GeneratedWorldBootstrap withTimeScale(
            WorldAtlasGenerator atlasGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            SimulationTimeScale timeScale) {
        AtmosphericRuntimePlan plan = requirePolicy(atmosphericForcingPolicy).runtimePlan();
        return new GeneratedWorldBootstrap(
                new GeneratedWorldPreparation(atlasGenerator),
                GeneratedWorldRuntimeBootstrap.withTimeScale(plan, timeScale));
    }

    @SuppressWarnings("removal")
    public static GeneratedWorldBootstrap withTimeScale(
            WorldAtlasGenerator atlasGenerator,
            TerrainMaterialGenerator terrainMaterialGenerator,
            AtmosphericForcingPolicy atmosphericForcingPolicy,
            SimulationTimeScale timeScale) {
        AtmosphericRuntimePlan plan = requirePolicy(atmosphericForcingPolicy).runtimePlan();
        return new GeneratedWorldBootstrap(
                new GeneratedWorldPreparation(atlasGenerator, terrainMaterialGenerator),
                GeneratedWorldRuntimeBootstrap.withTimeScale(plan, timeScale));
    }

    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            CompiledTerrainProfile profile,
            TerrainMaterialBindings bindings) {
        if (genesis == null || assembly == null || profile == null || bindings == null) {
            throw new IllegalArgumentException("generated world bootstrap inputs must not be null");
        }
        PreparedGeneratedWorld prepared = preparation.prepare(genesis, profile);
        return runtimeBootstrap.start(prepared, assembly, bindings);
    }

    /** Compatibility/custom path for callers that intentionally own terrain material resolution. */
    public GeneratedWorldRuntime create(
            WorldGenesis genesis,
            SimulationAssembly assembly,
            TerrainMaterialResolver materials) {
        if (genesis == null || assembly == null || materials == null) {
            throw new IllegalArgumentException("generated world bootstrap inputs must not be null");
        }
        WorldAtlas atlas = preparation.generateFacts(genesis);
        return runtimeBootstrap.start(atlas, assembly, materials);
    }

    @SuppressWarnings("removal")
    private static AtmosphericForcingPolicy requirePolicy(AtmosphericForcingPolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("atmospheric forcing policy must not be null");
        }
        return policy;
    }
}
