package io.github.evoforge.simulation.world.agent.need.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.evoforge.simulation.time.SimulationClock;
import io.github.evoforge.simulation.world.agent.need.NeedDeficitIncrease;
import io.github.evoforge.simulation.world.agent.need.NeedId;
import io.github.evoforge.simulation.world.agent.need.NeedLookup;
import io.github.evoforge.simulation.world.object.ObjectId;
import io.github.evoforge.simulation.world.object.ObjectLookup;
import io.github.evoforge.simulation.world.object.WorldObject;
import io.github.evoforge.simulation.world.object.definition.ObjectDefinitionId;
import org.junit.jupiter.api.Test;

final class NeedProgressionRateResolverTest {
    private static final NeedId NEED = NeedId.of("test:deficit");

    @Test
    void substitutedResolverCanSuppressOrIncreaseProgressionWithoutChangingSystem() {
        assertEquals(0, evaluate((objectId, definition) -> 0));
        assertEquals(9, evaluate((objectId, definition) -> 9));
    }

    private static long evaluate(NeedProgressionRateResolver resolver) {
        ObjectId id = ObjectId.of(0, 0);
        ObjectDefinitionId definitionId = ObjectDefinitionId.of(0);
        TestObject object = new TestObject(id, definitionId);
        ObjectLookup objects = new ObjectLookup() {
            @Override public WorldObject get(ObjectId candidate) { return id.equals(candidate) ? object : null; }
            @Override public boolean isAlive(ObjectId candidate) { return id.equals(candidate); }
            @Override public int size() { return 1; }
        };
        NeedProgressionDefinitions definitions = new NeedProgressionDefinitions();
        definitions.add(definitionId, new NeedProgressionDefinition(NEED, 5, 2));
        MutableNeeds needs = new MutableNeeds(id);
        SimulationClock clock = new SimulationClock();
        NeedProgressionSystem system = new NeedProgressionSystem(
                objects, definitions, needs, needs, resolver, clock);
        long[] processId = {-1};
        system.bindScheduler((delayTicks, scheduledProcessId) -> processId[0] = scheduledProcessId);
        system.activate(id);
        clock.advance();
        clock.advance();
        system.resume(processId[0]);
        return needs.level(id, NEED);
    }

    private static final class TestObject extends WorldObject {
        private TestObject(ObjectId id, ObjectDefinitionId definitionId) { super(id, definitionId); }
    }

    private static final class MutableNeeds implements NeedLookup, NeedDeficitIncrease {
        private final ObjectId objectId;
        private long level;

        private MutableNeeds(ObjectId objectId) { this.objectId = objectId; }
        @Override public boolean has(ObjectId objectId, NeedId needId) {
            return this.objectId.equals(objectId) && NEED.equals(needId);
        }
        @Override public long level(ObjectId objectId, NeedId needId) { return level; }
        @Override public long maxLevel(ObjectId objectId, NeedId needId) { return 100; }
        @Override public int needCount(ObjectId objectId) { return this.objectId.equals(objectId) ? 1 : 0; }
        @Override public NeedId needAt(ObjectId objectId, int index) {
            if (!this.objectId.equals(objectId) || index != 0) throw new IllegalArgumentException();
            return NEED;
        }
        @Override public long increase(ObjectId objectId, NeedId needId, long requestedAmount) {
            long applied = Math.min(100 - level, requestedAmount);
            level += applied;
            return applied;
        }
    }
}
