package io.github.evoforge.simulation.agents.need;

import io.github.evoforge.simulation.world.object.ObjectId;

/** Read-only view of authoritative per-object need state. */
public interface NeedLookup {

    boolean has(ObjectId objectId, NeedId needId);

    long level(ObjectId objectId, NeedId needId);

    long maxLevel(ObjectId objectId, NeedId needId);

    int needCount(ObjectId objectId);

    NeedId needAt(ObjectId objectId, int index);
}
