package io.github.evoforge.simulation.world.object;

import io.github.evoforge.simulation.world.definition.DefinitionId;
import io.github.evoforge.simulation.world.definition.DefinitionRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ObjectFactoryTest {

        @Test
        void createsWorldObjectFromDefinitionKey() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                WorldObject object = factory.create("core:test");

                assertEquals(
                                definitionId,
                                object.definitionId());

                assertTrue(
                                objects.isAlive(object.id()));

                assertSame(
                                object,
                                objects.get(object.id()));
        }

        @Test
        void createsWorldObjectFromDefinitionId() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                WorldObject object = factory.create(definitionId);

                assertEquals(
                                definitionId,
                                object.definitionId());

                assertTrue(
                                objects.isAlive(object.id()));

                assertSame(
                                object,
                                objects.get(object.id()));
        }

        @Test
        void createsSpecializedObjectFromDefinitionKey() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                TestWorldObject object = factory.create(
                                "core:test",
                                TestWorldObject::new);

                assertEquals(
                                definitionId,
                                object.definitionId());

                assertTrue(
                                objects.isAlive(object.id()));

                assertSame(
                                object,
                                objects.get(object.id()));
        }

        @Test
        void createsSpecializedObjectFromDefinitionId() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                TestWorldObject object = factory.create(
                                definitionId,
                                TestWorldObject::new);

                assertEquals(
                                definitionId,
                                object.definitionId());

                assertTrue(
                                objects.isAlive(object.id()));

                assertSame(
                                object,
                                objects.get(object.id()));
        }

        @Test
        void assignsSequentialObjectIds() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                WorldObject first = factory.create(definitionId);

                WorldObject second = factory.create(definitionId);

                assertEquals(
                                0,
                                first.id().slot());

                assertEquals(
                                1,
                                second.id().slot());
        }

        @Test
        void rejectsUnknownDefinitionKey() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                "core:unknown"));
        }

        @Test
        void rejectsUnknownDefinitionKeyWithCreator() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                "core:unknown",
                                                TestWorldObject::new));
        }

        @Test
        void rejectsUnknownDefinitionId() {
                DefinitionRegistry definitions = new DefinitionRegistry();

                definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                definitions);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                DefinitionId.of(1_000_000)));
        }

        @Test
        void rejectsUnknownDefinitionIdWithCreator() {
                DefinitionRegistry definitions = new DefinitionRegistry();

                definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                definitions);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                DefinitionId.of(1_000_000),
                                                TestWorldObject::new));
        }

        @Test
        void rejectsWrongDefinitionIdFromCreator() {
                ObjectRepository objects = new ObjectRepository();

                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                objects,
                                definitions);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                definitionId,
                                                (objectId, suppliedDefinitionId) -> new TestWorldObject(
                                                                objectId,
                                                                DefinitionId.of(100))));

                assertEquals(
                                0,
                                objects.size());
        }

        @Test
        void rejectsNullDefinitionKey() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                (String) null));
        }

        @Test
        void rejectsNullDefinitionKeyWithCreator() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                (String) null,
                                                TestWorldObject::new));
        }

        @Test
        void rejectsNullDefinitionId() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                (DefinitionId) null));
        }

        @Test
        void rejectsNullDefinitionIdWithCreator() {
                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                new DefinitionRegistry());

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                (DefinitionId) null,
                                                TestWorldObject::new));
        }

        @Test
        void rejectsNullCreatorForDefinitionKey() {
                DefinitionRegistry definitions = new DefinitionRegistry();

                definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                definitions);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                "core:test",
                                                null));
        }

        @Test
        void rejectsNullCreatorForDefinitionId() {
                DefinitionRegistry definitions = new DefinitionRegistry();

                DefinitionId definitionId = definitions.register("core:test");

                ObjectFactory factory = new ObjectFactory(
                                new ObjectRepository(),
                                definitions);

                assertThrows(
                                IllegalArgumentException.class,
                                () -> factory.create(
                                                definitionId,
                                                null));
        }

        @Test
        void rejectsNullRepository() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ObjectFactory(
                                                null,
                                                new DefinitionRegistry()));
        }

        @Test
        void rejectsNullDefinitions() {
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new ObjectFactory(
                                                new ObjectRepository(),
                                                null));
        }

        private static final class TestWorldObject
                        extends WorldObject {

                private TestWorldObject(
                                ObjectId id,
                                DefinitionId definitionId) {

                        super(
                                        id,
                                        definitionId);
                }
        }
}