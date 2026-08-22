package io.github.evoforge.simulation.kernel.scheduling;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandlerRegistryTest {

        @Test
        void registersHandler() {
                HandlerRegistry registry = new HandlerRegistry();

                ScheduledHandler handler = processId -> {
                };

                HandlerId id = registry.register(handler);

                assertEquals(
                                HandlerId.of(0),
                                id);

                assertSame(
                                handler,
                                registry.get(id));
        }

        @Test
        void assignsSequentialIds() {
                HandlerRegistry registry = new HandlerRegistry();

                HandlerId first = registry.register(
                                processId -> {
                                });

                HandlerId second = registry.register(
                                processId -> {
                                });

                assertEquals(
                                HandlerId.of(0),
                                first);

                assertEquals(
                                HandlerId.of(1),
                                second);
        }

        @Test
        void containsRegisteredHandler() {
                HandlerRegistry registry = new HandlerRegistry();

                HandlerId id = registry.register(
                                processId -> {
                                });

                assertTrue(
                                registry.contains(id));
        }

        @Test
        void doesNotContainUnknownHandler() {
                HandlerRegistry registry = new HandlerRegistry();

                registry.register(
                                processId -> {
                                });

                assertFalse(
                                registry.contains(
                                                HandlerId.of(100)));
        }

        @Test
        void returnsNullForUnknownHandler() {
                HandlerRegistry registry = new HandlerRegistry();

                assertNull(
                                registry.get(
                                                HandlerId.of(100)));
        }

        @Test
        void rejectsNullHandler() {
                HandlerRegistry registry = new HandlerRegistry();

                assertThrows(
                                IllegalArgumentException.class,
                                () -> registry.register(null));
        }

        @Test
        void reportsSize() {
                HandlerRegistry registry = new HandlerRegistry();

                registry.register(
                                processId -> {
                                });

                registry.register(
                                processId -> {
                                });

                assertEquals(
                                2,
                                registry.size());
        }
}