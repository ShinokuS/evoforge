package io.github.evoforge.simulation.world.agent.need.progression;

import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only diagnostics for active Need progression processes. */
public interface NeedProgressionLookup {
    boolean has(ObjectId objectId, NeedId needId);
    long nextEvaluationTick(ObjectId objectId, NeedId needId);
    NeedProgressionTrace lastEvaluation(ObjectId objectId, NeedId needId);
}
