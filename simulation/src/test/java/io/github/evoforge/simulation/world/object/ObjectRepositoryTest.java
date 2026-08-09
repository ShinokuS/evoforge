package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.definition.DefinitionId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectRepositoryTest {

    @Test
    void createsObject() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertEquals(0, object.id().slot());
        assertEquals(0, object.id().generation());
        assertEquals(1, repository.size());
    }

    @Test
    void createsObjectsInDifferentSlots() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject first = repository.create(TestWorldObject::new);
        TestWorldObject second = repository.create(TestWorldObject::new);

        assertEquals(0, first.id().slot());
        assertEquals(1, second.id().slot());
        assertEquals(2, repository.size());
    }

    @Test
    void returnsObjectById() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertSame(object, repository.get(object.id()));
    }

    @Test
    void reportsExistingObjectAsAlive() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertTrue(repository.isAlive(object.id()));
    }

    @Test
    void removesObject() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertTrue(repository.remove(object.id()));
        assertEquals(0, repository.size());
        assertFalse(repository.isAlive(object.id()));
        assertNull(repository.get(object.id()));
    }

    @Test
    void reusesRemovedSlotWithNewGeneration() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject first = repository.create(TestWorldObject::new);
        ObjectId oldId = first.id();

        repository.remove(oldId);

        TestWorldObject second = repository.create(TestWorldObject::new);

        assertEquals(oldId.slot(), second.id().slot());
        assertEquals(oldId.generation() + 1, second.id().generation());
    }

    @Test
    void oldIdDoesNotReferenceNewObject() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject first = repository.create(TestWorldObject::new);
        ObjectId oldId = first.id();

        repository.remove(oldId);

        TestWorldObject second = repository.create(TestWorldObject::new);

        assertFalse(repository.isAlive(oldId));
        assertNull(repository.get(oldId));
        assertSame(second, repository.get(second.id()));
    }

    @Test
    void removingSameObjectTwiceFails() {
        ObjectRepository repository = new ObjectRepository();

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertTrue(repository.remove(object.id()));
        assertFalse(repository.remove(object.id()));
        assertEquals(0, repository.size());
    }

    @Test
    void handlesNullId() {
        ObjectRepository repository = new ObjectRepository();

        assertFalse(repository.isAlive(null));
        assertNull(repository.get(null));
        assertFalse(repository.remove(null));
    }

    @Test
    void rejectsNullFactory() {
        ObjectRepository repository = new ObjectRepository();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(null));
    }

    @Test
    void rejectsFactoryReturningNull() {
        ObjectRepository repository = new ObjectRepository();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(id -> null));

        assertEquals(0, repository.size());
    }

    @Test
    void rejectsObjectWithDifferentId() {
        ObjectRepository repository = new ObjectRepository();

        assertThrows(
                IllegalArgumentException.class,
                () -> repository.create(
                        id -> new TestWorldObject(
                                ObjectId.of(id.slot(), id.generation() + 1))));

        assertEquals(0, repository.size());
    }

    @Test
    void releasesSlotWhenFactoryFails() {
        ObjectRepository repository = new ObjectRepository();

        assertThrows(
                RuntimeException.class,
                () -> repository.create(id -> {
                    throw new RuntimeException("creation failed");
                }));

        TestWorldObject object = repository.create(TestWorldObject::new);

        assertEquals(0, object.id().slot());
        assertEquals(0, object.id().generation());
        assertEquals(1, repository.size());
    }

    @Test
    void growsBeyondInitialCapacity() {
        ObjectRepository repository = new ObjectRepository(1);

        TestWorldObject first = repository.create(TestWorldObject::new);
        TestWorldObject second = repository.create(TestWorldObject::new);
        TestWorldObject third = repository.create(TestWorldObject::new);

        assertEquals(0, first.id().slot());
        assertEquals(1, second.id().slot());
        assertEquals(2, third.id().slot());
        assertEquals(3, repository.size());
    }

    @Test
    void rejectsInvalidInitialCapacity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectRepository(0));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ObjectRepository(-1));
    }

    private static final class TestWorldObject extends WorldObject {

        private TestWorldObject(ObjectId id) {
            super(id, DefinitionId.of(0));
        }
    }
}