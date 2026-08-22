package io.github.evoforge.simulation.world.object.stock.growth;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only current growth process diagnostics. */
public interface GrowthLookup {
    boolean has(ObjectId objectId);
    GrowthStatus status(ObjectId objectId);
    long nextEvaluationTick(ObjectId objectId);
    GrowthTrace lastEvaluation(ObjectId objectId);
}
