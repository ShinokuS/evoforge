package io.github.evoforge.simulation.world.liquid;

/** Mutable planned edge transfer shared by deterministic flow planning and limiting phases. */
final class LiquidTransfer {
    final LiquidCell source;
    final LiquidCell destination;
    final LiquidTypeId type;
    int amount;

    LiquidTransfer(
            LiquidCell source,
            LiquidCell destination,
            LiquidTypeId type,
            int amount) {
        if (source == null || destination == null || type == null) {
            throw new IllegalArgumentException("liquid transfer identity must not be null");
        }
        this.source = source;
        this.destination = destination;
        this.type = type;
        this.amount = amount;
    }
}
