package io.github.evoforge.simulation.runtime;

import io.github.evoforge.simulation.world.liquid.LiquidFlowProcess;
import io.github.evoforge.simulation.world.liquid.LiquidFlowSystem;
import io.github.evoforge.simulation.world.liquid.LiquidSurfaceRetentionLookup;

/** Runtime environment capabilities needed by later assemblies and the read view. */
record EnvironmentRuntime(
        LiquidSurfaceRetentionLookup surfaceRetention,
        LiquidFlowSystem liquidFlow,
        LiquidFlowProcess liquidFlowProcess) { }
