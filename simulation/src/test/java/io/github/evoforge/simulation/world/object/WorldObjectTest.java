package io.github.evoforge.simulation.world.object;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorldObjectTest {

    @Test
    void storesObjectId() {
        ObjectId id = ObjectId.of(42, 7);
        WorldObject object = new TestWorldObject(id);

        assertEquals(id, object.id());
    }

    @Test
    void rejectsNullId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new TestWorldObject(null));
    }

    private static final class TestWorldObject extends WorldObject {

        private TestWorldObject(ObjectId id) {
            super(id);
        }
    }
}