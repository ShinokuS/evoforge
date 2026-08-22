package io.github.evoforge.simulation.world.liquid;

/** Optional deterministic exchange step that must run before a liquid-flow solve. */
@FunctionalInterface
public interface LiquidFlowPreparation {

    LiquidFlowPreparation NONE = () -> { };

    void prepare();
}
