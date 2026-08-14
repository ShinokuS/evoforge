package io.github.evoforge.simulation.world.mechanics.consumption;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Composition-root relay that lets the stock owner notify one domain consumer without depending on it. */
public final class ConsumableStockReductionRelay implements ConsumableStockReductionSink {
    private ConsumableStockReductionSink sink;

    public void bind(ConsumableStockReductionSink sink) {
        if (sink == null) throw new IllegalArgumentException("sink must not be null");
        if (this.sink != null) throw new IllegalStateException("consumable stock reduction sink is already bound");
        this.sink = sink;
    }

    @Override
    public void stockReduced(ObjectId objectId) {
        if (sink != null) sink.stockReduced(objectId);
    }
}
