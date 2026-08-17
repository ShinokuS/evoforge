package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowProcess;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.landscape.liquid.LiquidSurfaceRetentionLookup;

/** Runtime environment capabilities needed by later assemblies and the read view. */
record EnvironmentRuntime(
        LiquidSurfaceRetentionLookup surfaceRetention,
        LiquidFlowSystem liquidFlow,
        LiquidFlowProcess liquidFlowProcess) { }
